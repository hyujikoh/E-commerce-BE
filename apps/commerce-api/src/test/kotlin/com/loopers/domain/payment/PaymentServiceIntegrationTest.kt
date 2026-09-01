package com.loopers.domain.payment

import com.loopers.domain.accommodation.Reservation
import com.loopers.domain.accommodation.ReservationNightly
import com.loopers.domain.accommodation.vo.DateRange
import com.loopers.domain.accommodation.vo.Money
import com.loopers.infrastructure.accommodation.ReservationJpaRepository
import com.loopers.infrastructure.payment.PaymentJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

@SpringBootTest
class PaymentServiceIntegrationTest @Autowired constructor(
    private val paymentService: PaymentService,
    private val reservationJpaRepository: ReservationJpaRepository,
    private val paymentJpaRepository: PaymentJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val guestId = 1L
    private val cardNo = "1234-5678-9012-3456"

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun seedReservation(
        guestId: Long = this.guestId,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): Reservation {
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
                now = now,
            ),
        )
    }

    @DisplayName("결제를 생성할 때,")
    @Nested
    inner class Create {
        @DisplayName("PENDING 예약이면 CREATED 결제를 만들고, 금액·주문 ID·게스트 ID 를 예약에서 확정한다.")
        @Test
        fun createsPayment_fromReservationSnapshot() {
            val reservation = seedReservation()

            val creation = paymentService.create(reservation.id, guestId, CardType.SAMSUNG, cardNo)

            assertAll(
                { assertThat(creation.isNew).isTrue() },
                { assertThat(creation.payment.status).isEqualTo(PaymentStatus.CREATED) },
                { assertThat(creation.payment.amount).isEqualTo(Money.krw(100_000)) },
                { assertThat(creation.payment.orderId).isEqualTo(Payment.orderIdOf(reservation.id)) },
                { assertThat(creation.payment.guestId).isEqualTo(guestId) },
            )
        }

        @DisplayName("진행 중인 결제가 있으면 새로 만들지 않고 그 결제를 반환한다(멱등).")
        @Test
        fun returnsExistingPayment_whenInProgress() {
            val reservation = seedReservation()
            val first = paymentService.create(reservation.id, guestId, CardType.SAMSUNG, cardNo)

            val second = paymentService.create(reservation.id, guestId, CardType.KB, cardNo)

            assertAll(
                { assertThat(second.isNew).isFalse() },
                { assertThat(second.payment.id).isEqualTo(first.payment.id) },
                { assertThat(paymentJpaRepository.findByReservationId(reservation.id)).hasSize(1) },
            )
        }

        @DisplayName("실패한 결제만 있으면 재시도로 새 결제를 생성한다.")
        @Test
        fun createsNewPayment_whenPreviousFailed() {
            val reservation = seedReservation()
            val first = paymentService.create(reservation.id, guestId, CardType.SAMSUNG, cardNo)
            paymentService.markRequestFailed(first.payment.id, "서버 불안정")

            val second = paymentService.create(reservation.id, guestId, CardType.KB, cardNo)

            assertAll(
                { assertThat(second.isNew).isTrue() },
                { assertThat(second.payment.id).isNotEqualTo(first.payment.id) },
                { assertThat(paymentJpaRepository.findByReservationId(reservation.id)).hasSize(2) },
            )
        }

        @DisplayName("본인 예약이 아니면 RESERVATION_NOT_OWNED 예외가 발생한다.")
        @Test
        fun throwsNotOwned_whenGuestMismatch() {
            val reservation = seedReservation(guestId = 999L)

            val exception = assertThrows<CoreException> {
                paymentService.create(reservation.id, guestId, CardType.SAMSUNG, cardNo)
            }
            assertThat(exception.errorType).isEqualTo(ErrorType.RESERVATION_NOT_OWNED)
        }

        @DisplayName("홀드가 만료된 예약이면 INVALID_RESERVATION_STATE 예외가 발생한다.")
        @Test
        fun throwsInvalidState_whenHoldExpired() {
            val reservation = seedReservation(now = ZonedDateTime.now().minusMinutes(20))

            val exception = assertThrows<CoreException> {
                paymentService.create(reservation.id, guestId, CardType.SAMSUNG, cardNo)
            }
            assertThat(exception.errorType).isEqualTo(ErrorType.INVALID_RESERVATION_STATE)
        }

        @DisplayName("PENDING 이 아닌 예약이면 INVALID_RESERVATION_STATE 예외가 발생한다.")
        @Test
        fun throwsInvalidState_whenNotPending() {
            val reservation = seedReservation()
            reservation.confirm()
            reservationJpaRepository.save(reservation)

            val exception = assertThrows<CoreException> {
                paymentService.create(reservation.id, guestId, CardType.SAMSUNG, cardNo)
            }
            assertThat(exception.errorType).isEqualTo(ErrorType.INVALID_RESERVATION_STATE)
        }

        @DisplayName("존재하지 않는 예약이면 RESERVATION_NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenReservationMissing() {
            val exception = assertThrows<CoreException> {
                paymentService.create(-1L, guestId, CardType.SAMSUNG, cardNo)
            }
            assertThat(exception.errorType).isEqualTo(ErrorType.RESERVATION_NOT_FOUND)
        }
    }

    @DisplayName("PG 결과를 반영할 때,")
    @Nested
    inner class ApplyPgResult {
        private fun pgTransaction(status: PgTransactionStatus, reason: String? = null) =
            PgTransaction(transactionKey = "tx-1", orderId = "000001", status = status, reason = reason)

        @DisplayName("SUCCESS 스냅샷이면 결제를 성공 종결하고 SUCCEEDED 를 반환한다.")
        @Test
        fun succeedsPayment_onSuccessSnapshot() {
            val reservation = seedReservation()
            val payment = paymentService.create(reservation.id, guestId, CardType.SAMSUNG, cardNo).payment

            val outcome = paymentService.applyPgResult(payment.id, pgTransaction(PgTransactionStatus.SUCCESS))

            val saved = paymentJpaRepository.findById(payment.id).get()
            assertAll(
                { assertThat(outcome).isEqualTo(PgResultOutcome.SUCCEEDED) },
                { assertThat(saved.status).isEqualTo(PaymentStatus.SUCCESS) },
                { assertThat(saved.transactionKey).isEqualTo("tx-1") },
            )
        }

        @DisplayName("FAILED 스냅샷이면 사유와 함께 실패 종결하고 FAILED 를 반환한다.")
        @Test
        fun failsPayment_onFailedSnapshot() {
            val reservation = seedReservation()
            val payment = paymentService.create(reservation.id, guestId, CardType.SAMSUNG, cardNo).payment

            val outcome = paymentService.applyPgResult(payment.id, pgTransaction(PgTransactionStatus.FAILED, "한도초과"))

            val saved = paymentJpaRepository.findById(payment.id).get()
            assertAll(
                { assertThat(outcome).isEqualTo(PgResultOutcome.FAILED) },
                { assertThat(saved.status).isEqualTo(PaymentStatus.FAILED) },
                { assertThat(saved.failureReason).isEqualTo("한도초과") },
            )
        }

        @DisplayName("PENDING 스냅샷이면 접수 사실(REQUESTED)만 반영하고 NONE 을 반환한다.")
        @Test
        fun marksRequested_onPendingSnapshot() {
            val reservation = seedReservation()
            val payment = paymentService.create(reservation.id, guestId, CardType.SAMSUNG, cardNo).payment

            val outcome = paymentService.applyPgResult(payment.id, pgTransaction(PgTransactionStatus.PENDING))

            val saved = paymentJpaRepository.findById(payment.id).get()
            assertAll(
                { assertThat(outcome).isEqualTo(PgResultOutcome.NONE) },
                { assertThat(saved.status).isEqualTo(PaymentStatus.REQUESTED) },
                { assertThat(saved.transactionKey).isEqualTo("tx-1") },
            )
        }

        @DisplayName("이미 종결된 결제에 다시 반영하면 아무것도 바꾸지 않고 NONE 을 반환한다(중복 콜백 멱등).")
        @Test
        fun returnsNone_whenAlreadyTerminal() {
            val reservation = seedReservation()
            val payment = paymentService.create(reservation.id, guestId, CardType.SAMSUNG, cardNo).payment
            paymentService.applyPgResult(payment.id, pgTransaction(PgTransactionStatus.SUCCESS))

            val outcome = paymentService.applyPgResult(payment.id, pgTransaction(PgTransactionStatus.FAILED, "한도초과"))

            val saved = paymentJpaRepository.findById(payment.id).get()
            assertAll(
                { assertThat(outcome).isEqualTo(PgResultOutcome.NONE) },
                { assertThat(saved.status).isEqualTo(PaymentStatus.SUCCESS) },
            )
        }
    }

    @DisplayName("접수 결과를 반영할 때,")
    @Nested
    inner class MarkRequested {
        @DisplayName("콜백이 먼저 결과를 종결했다면 접수 반영은 no-op 이다(접수 응답 지연 경쟁).")
        @Test
        fun keepsTerminalState_whenCallbackArrivedFirst() {
            val reservation = seedReservation()
            val payment = paymentService.create(reservation.id, guestId, CardType.SAMSUNG, cardNo).payment
            paymentService.applyPgResult(
                payment.id,
                PgTransaction("tx-1", "000001", PgTransactionStatus.SUCCESS, null),
            )

            val result = paymentService.markRequested(payment.id, "tx-1")

            assertThat(result.status).isEqualTo(PaymentStatus.SUCCESS)
        }
    }
}
