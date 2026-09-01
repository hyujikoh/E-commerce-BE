package com.loopers.application.payment

import com.loopers.domain.accommodation.Reservation
import com.loopers.domain.accommodation.ReservationNightly
import com.loopers.domain.accommodation.ReservationStatus
import com.loopers.domain.accommodation.vo.DateRange
import com.loopers.domain.accommodation.vo.Money
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgPaymentGateway
import com.loopers.domain.payment.PgTransaction
import com.loopers.domain.payment.PgTransactionStatus
import com.loopers.infrastructure.accommodation.ReservationJpaRepository
import com.loopers.infrastructure.payment.PaymentJpaRepository
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
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * 결제 상태 동기화(콜백 유실·접수 불명 복구) 통합 테스트.
 * 백그라운드 스케줄러가 시나리오에 간섭하지 않도록 스위치로 끄고, Facade 를 직접 호출한다.
 * 방금 만든 결제를 스캔 대상에 넣기 위해 threshold 는 미래 시각을 주입한다.
 */
@SpringBootTest(properties = ["scheduler.payment-sync.enabled=false"])
class PaymentSyncIntegrationTest @Autowired constructor(
    private val paymentFacade: PaymentFacade,
    private val paymentService: PaymentService,
    private val reservationJpaRepository: ReservationJpaRepository,
    private val paymentJpaRepository: PaymentJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @MockkBean
    private lateinit var pgPaymentGateway: PgPaymentGateway

    private val guestId = 1L
    private val cardNo = "1234-5678-9012-3456"
    private val futureThreshold: ZonedDateTime get() = ZonedDateTime.now().plusMinutes(5)

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun seedReservation(): Reservation {
        val checkIn = LocalDate.of(2026, 6, 10)
        return reservationJpaRepository.save(
            Reservation.create(
                guestId = guestId,
                roomTypeId = 100L,
                stayPeriod = DateRange(checkIn, checkIn.plusDays(2)),
                nightlySnapshots = listOf(
                    ReservationNightly(checkIn, Money.krw(50_000)),
                    ReservationNightly(checkIn.plusDays(1), Money.krw(50_000)),
                ),
                holdDuration = Duration.ofMinutes(10),
                now = ZonedDateTime.now(),
            ),
        )
    }

    private fun createPayment(reservationId: Long): Payment =
        paymentService.create(reservationId, guestId, CardType.SAMSUNG, cardNo).payment

    @DisplayName("상태 동기화를 실행할 때,")
    @Nested
    inner class SyncPendingResults {
        @DisplayName("콜백이 유실된 REQUESTED 결제는, PG 단건 조회로 결과를 확인해 결제 성공·예약 확정으로 복구한다.")
        @Test
        fun recoversLostCallback_forRequestedPayment() {
            val reservation = seedReservation()
            val payment = createPayment(reservation.id)
            paymentService.markRequested(payment.id, "tx-1")
            every { pgPaymentGateway.findTransaction(guestId, "tx-1") } returns
                PgTransaction("tx-1", payment.orderId, PgTransactionStatus.SUCCESS, null)

            val synced = paymentFacade.syncPendingResults(threshold = futureThreshold)

            val saved = paymentJpaRepository.findById(payment.id).get()
            assertAll(
                { assertThat(synced).isEqualTo(1) },
                { assertThat(saved.status).isEqualTo(PaymentStatus.SUCCESS) },
                { assertThat(reservationJpaRepository.findById(reservation.id).get().status).isEqualTo(ReservationStatus.CONFIRMED) },
            )
        }

        @DisplayName("접수 불명(CREATED) 결제에 PG 거래가 없음이 확정되면(빈 목록), REQUEST_FAILED 로 종결해 재시도를 열어준다.")
        @Test
        fun marksRequestFailed_whenPgConfirmsNoTransaction() {
            val reservation = seedReservation()
            val payment = createPayment(reservation.id)
            every { pgPaymentGateway.findTransactionsByOrderId(guestId, payment.orderId) } returns emptyList()

            paymentFacade.syncPendingResults(threshold = futureThreshold)

            val saved = paymentJpaRepository.findById(payment.id).get()
            assertAll(
                { assertThat(saved.status).isEqualTo(PaymentStatus.REQUEST_FAILED) },
                { assertThat(saved.failureReason).isNotBlank() },
                { assertThat(reservationJpaRepository.findById(reservation.id).get().status).isEqualTo(ReservationStatus.PENDING) },
            )
        }

        @DisplayName("접수 불명(CREATED) 결제는, 다른 결제에 이미 귀속된 거래를 제외하고 남은 거래의 결과를 반영한다.")
        @Test
        fun settlesWithUnclaimedTransaction_excludingClaimedOnes() {
            val reservation = seedReservation()
            val first = createPayment(reservation.id)
            paymentService.applyPgResult(first.id, PgTransaction("tx-old", first.orderId, PgTransactionStatus.FAILED, "한도 초과"))
            val second = createPayment(reservation.id)
            every { pgPaymentGateway.findTransactionsByOrderId(guestId, second.orderId) } returns listOf(
                PgTransaction("tx-old", second.orderId, PgTransactionStatus.FAILED, "한도 초과"),
                PgTransaction("tx-new", second.orderId, PgTransactionStatus.SUCCESS, null),
            )

            paymentFacade.syncPendingResults(threshold = futureThreshold)

            val saved = paymentJpaRepository.findById(second.id).get()
            assertAll(
                { assertThat(saved.status).isEqualTo(PaymentStatus.SUCCESS) },
                { assertThat(saved.transactionKey).isEqualTo("tx-new") },
                { assertThat(reservationJpaRepository.findById(reservation.id).get().status).isEqualTo(ReservationStatus.CONFIRMED) },
            )
        }

        @DisplayName("PG 조회가 실패하면(존재 불명), 실패 확정하지 않고 그대로 둔다 — 다음 회차에 재시도한다.")
        @Test
        fun keepsPayment_whenPgQueryFails() {
            val reservation = seedReservation()
            val payment = createPayment(reservation.id)
            every { pgPaymentGateway.findTransactionsByOrderId(guestId, payment.orderId) } returns null

            paymentFacade.syncPendingResults(threshold = futureThreshold)

            assertThat(paymentJpaRepository.findById(payment.id).get().status).isEqualTo(PaymentStatus.CREATED)
        }

        @DisplayName("유예 시간이 지나지 않은 결제는 스캔하지 않는다 — 콜백이 도착할 시간을 준다.")
        @Test
        fun skipsFreshPayment_withinGracePeriod() {
            val reservation = seedReservation()
            createPayment(reservation.id)

            val synced = paymentFacade.syncPendingResults()

            assertAll(
                { assertThat(synced).isEqualTo(0) },
                { verify(exactly = 0) { pgPaymentGateway.findTransactionsByOrderId(any(), any()) } },
            )
        }
    }
}
