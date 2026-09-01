package com.loopers.interfaces.api

import com.loopers.domain.accommodation.CancelReason
import com.loopers.domain.accommodation.DailyRoomInventory
import com.loopers.domain.accommodation.DailyRoomRate
import com.loopers.domain.accommodation.ReservationStatus
import com.loopers.domain.accommodation.vo.Money
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgPaymentGateway
import com.loopers.domain.payment.PgRequestResult
import com.loopers.domain.payment.PgTransaction
import com.loopers.domain.payment.PgTransactionStatus
import com.loopers.infrastructure.accommodation.DailyRoomInventoryJpaRepository
import com.loopers.infrastructure.accommodation.DailyRoomRateJpaRepository
import com.loopers.infrastructure.accommodation.ReservationJpaRepository
import com.loopers.infrastructure.payment.PaymentJpaRepository
import com.loopers.interfaces.api.accommodation.ReservationV1Dto
import com.loopers.interfaces.api.payment.PaymentV1Dto
import com.loopers.utils.DatabaseCleanUp
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val dailyRoomRateJpaRepository: DailyRoomRateJpaRepository,
    private val dailyRoomInventoryJpaRepository: DailyRoomInventoryJpaRepository,
    private val reservationJpaRepository: ReservationJpaRepository,
    private val paymentJpaRepository: PaymentJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @MockkBean
    private lateinit var pgPaymentGateway: PgPaymentGateway

    private val guestId = 1L
    private val roomTypeId = 100L
    private val dates = listOf(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 11))
    private val jsonHeaders = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    /** 요금·재고를 심고 실제 API 로 PENDING 예약을 만든다(재고 1개 점유). */
    private fun createReservation(): Long {
        dates.forEach { dailyRoomRateJpaRepository.save(DailyRoomRate(roomTypeId, it, Money.krw(50_000))) }
        dates.forEach { dailyRoomInventoryJpaRepository.save(DailyRoomInventory(roomTypeId, it, remaining = 1)) }
        val body = """{"guestId":$guestId,"roomTypeId":$roomTypeId,"checkIn":"2026-06-10","checkOut":"2026-06-12"}"""
        val responseType = object : ParameterizedTypeReference<ApiResponse<ReservationV1Dto.ReservationResponse>>() {}
        return testRestTemplate
            .exchange("/api/v1/reservations", HttpMethod.POST, HttpEntity(body, jsonHeaders), responseType)
            .body!!.data!!.id
    }

    private fun requestPay(reservationId: Long) = testRestTemplate.exchange(
        "/api/v1/payments",
        HttpMethod.POST,
        HttpEntity(
            """{"guestId":$guestId,"reservationId":$reservationId,"cardType":"SAMSUNG","cardNo":"1234-5678-9012-3456"}""",
            jsonHeaders,
        ),
        object : ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>>() {},
    )

    private fun requestCallback(orderId: String, transactionKey: String) = testRestTemplate.exchange(
        "/api/v1/payments/callback",
        HttpMethod.POST,
        HttpEntity("""{"transactionKey":"$transactionKey","orderId":"$orderId","status":"SUCCESS"}""", jsonHeaders),
        object : ParameterizedTypeReference<ApiResponse<Any>>() {},
    )

    @DisplayName("POST /api/v1/payments")
    @Nested
    inner class Pay {
        @DisplayName("PG 가 접수하면, REQUESTED 결제와 transactionKey 를 반환한다.")
        @Test
        fun returnsRequestedPayment_whenPgAccepts() {
            val reservationId = createReservation()
            every { pgPaymentGateway.requestPayment(any()) } returns PgRequestResult.Accepted("tx-1")

            val response = requestPay(reservationId)

            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.status).isEqualTo(PaymentStatus.REQUESTED) },
                { assertThat(response.body?.data?.transactionKey).isEqualTo("tx-1") },
                { assertThat(response.body?.data?.amount).isEqualTo(100_000) },
            )
        }

        @DisplayName("PG 가 거절하면, 예외 대신 REQUEST_FAILED 상태를 담아 정상 응답한다(Fallback).")
        @Test
        fun returnsRequestFailedPayment_whenPgRejects() {
            val reservationId = createReservation()
            every { pgPaymentGateway.requestPayment(any()) } returns PgRequestResult.Rejected("PG 가 결제 요청을 거절했습니다.")

            val response = requestPay(reservationId)

            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.status).isEqualTo(PaymentStatus.REQUEST_FAILED) },
                { assertThat(response.body?.data?.failureReason).isNotBlank() },
            )
        }

        @DisplayName("PG 응답이 유실되면(타임아웃), 실패 확정하지 않고 CREATED 상태로 정상 응답한다.")
        @Test
        fun keepsCreatedPayment_whenPgResultUnknown() {
            val reservationId = createReservation()
            every { pgPaymentGateway.requestPayment(any()) } returns PgRequestResult.Unknown("PG 응답을 받지 못했습니다.")

            val response = requestPay(reservationId)

            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.status).isEqualTo(PaymentStatus.CREATED) },
                { assertThat(response.body?.data?.transactionKey).isNull() },
            )
        }

        @DisplayName("진행 중 결제가 있는 예약에 다시 요청하면, PG 를 재호출하지 않고 같은 결제를 반환한다(멱등).")
        @Test
        fun returnsSamePaymentWithoutPgCall_whenAlreadyInProgress() {
            val reservationId = createReservation()
            every { pgPaymentGateway.requestPayment(any()) } returns PgRequestResult.Accepted("tx-1")

            val first = requestPay(reservationId)
            val second = requestPay(reservationId)

            assertAll(
                { assertThat(second.body?.data?.id).isEqualTo(first.body?.data?.id) },
                { verify(exactly = 1) { pgPaymentGateway.requestPayment(any()) } },
            )
        }
    }

    @DisplayName("POST /api/v1/payments/callback")
    @Nested
    inner class Callback {
        @DisplayName("PG 재조회로 SUCCESS 가 검증되면, 결제를 성공 종결하고 예약을 확정한다.")
        @Test
        fun confirmsReservation_whenVerifiedSuccess() {
            val reservationId = createReservation()
            val orderId = Payment.orderIdOf(reservationId)
            every { pgPaymentGateway.requestPayment(any()) } returns PgRequestResult.Accepted("tx-1")
            every { pgPaymentGateway.findTransaction(guestId, "tx-1") } returns
                PgTransaction("tx-1", orderId, PgTransactionStatus.SUCCESS, null)
            requestPay(reservationId)

            val response = requestCallback(orderId, "tx-1")

            val payment = paymentJpaRepository.findByReservationId(reservationId).single()
            val reservation = reservationJpaRepository.findById(reservationId).get()
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(payment.status).isEqualTo(PaymentStatus.SUCCESS) },
                { assertThat(reservation.status).isEqualTo(ReservationStatus.CONFIRMED) },
            )
        }

        @DisplayName("PG 재조회로 FAILED 가 검증되면, 결제를 실패 종결하고 예약을 취소(PAYMENT_FAILED)하며 재고를 복구한다.")
        @Test
        fun cancelsReservationAndRestoresInventory_whenVerifiedFailed() {
            val reservationId = createReservation()
            val orderId = Payment.orderIdOf(reservationId)
            every { pgPaymentGateway.requestPayment(any()) } returns PgRequestResult.Accepted("tx-1")
            every { pgPaymentGateway.findTransaction(guestId, "tx-1") } returns
                PgTransaction("tx-1", orderId, PgTransactionStatus.FAILED, "한도 초과")
            requestPay(reservationId)

            requestCallback(orderId, "tx-1")

            val payment = paymentJpaRepository.findByReservationId(reservationId).single()
            val reservation = reservationJpaRepository.findById(reservationId).get()
            assertAll(
                { assertThat(payment.status).isEqualTo(PaymentStatus.FAILED) },
                { assertThat(payment.failureReason).isEqualTo("한도 초과") },
                { assertThat(reservation.status).isEqualTo(ReservationStatus.CANCELLED) },
                { assertThat(reservation.cancelReason).isEqualTo(CancelReason.PAYMENT_FAILED) },
                { assertThat(dailyRoomInventoryJpaRepository.findAll()).allMatch { it.remaining == 1 } },
            )
        }

        @DisplayName("PG 재조회로 검증되지 않으면(조회 실패), 콜백 본문을 믿지 않고 결제를 바꾸지 않는다.")
        @Test
        fun ignoresCallback_whenVerificationFails() {
            val reservationId = createReservation()
            val orderId = Payment.orderIdOf(reservationId)
            every { pgPaymentGateway.requestPayment(any()) } returns PgRequestResult.Accepted("tx-1")
            every { pgPaymentGateway.findTransaction(guestId, "tx-1") } returns null
            requestPay(reservationId)

            val response = requestCallback(orderId, "tx-1")

            val payment = paymentJpaRepository.findByReservationId(reservationId).single()
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(payment.status).isEqualTo(PaymentStatus.REQUESTED) },
            )
        }

        @DisplayName("접수 응답이 유실된(CREATED) 결제도, 콜백이 검증되면 결과를 반영한다.")
        @Test
        fun settlesCreatedPayment_whenCallbackVerified() {
            val reservationId = createReservation()
            val orderId = Payment.orderIdOf(reservationId)
            every { pgPaymentGateway.requestPayment(any()) } returns PgRequestResult.Unknown("응답 유실")
            every { pgPaymentGateway.findTransaction(guestId, "tx-1") } returns
                PgTransaction("tx-1", orderId, PgTransactionStatus.SUCCESS, null)
            requestPay(reservationId)

            requestCallback(orderId, "tx-1")

            val payment = paymentJpaRepository.findByReservationId(reservationId).single()
            val reservation = reservationJpaRepository.findById(reservationId).get()
            assertAll(
                { assertThat(payment.status).isEqualTo(PaymentStatus.SUCCESS) },
                { assertThat(payment.transactionKey).isEqualTo("tx-1") },
                { assertThat(reservation.status).isEqualTo(ReservationStatus.CONFIRMED) },
            )
        }
    }
}
