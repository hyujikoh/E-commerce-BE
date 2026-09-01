package com.loopers.domain.payment

import com.loopers.domain.accommodation.Reservation
import com.loopers.domain.accommodation.ReservationNightly
import com.loopers.domain.accommodation.vo.DateRange
import com.loopers.domain.accommodation.vo.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.LocalDate

class PaymentTest {
    private val cardNo = "1234-5678-9012-3456"

    private fun reservation(discount: Long = 0): Reservation {
        val checkIn = LocalDate.of(2026, 6, 10)
        return Reservation.create(
            guestId = 1L,
            roomTypeId = 100L,
            stayPeriod = DateRange(checkIn, checkIn.plusDays(2)),
            nightlySnapshots = listOf(
                ReservationNightly(checkIn, Money.krw(50_000)),
                ReservationNightly(checkIn.plusDays(1), Money.krw(50_000)),
            ),
            holdDuration = Duration.ofMinutes(10),
            discountAmount = Money.krw(discount),
        )
    }

    private fun payment(): Payment = Payment.create(reservation(), CardType.SAMSUNG, cardNo)

    @DisplayName("결제를 생성할 때,")
    @Nested
    inner class Create {
        @DisplayName("예약의 최종 금액을 결제 금액 스냅샷으로 가져오고 CREATED 상태로 시작한다.")
        @Test
        fun createsWithFinalAmountSnapshot() {
            val payment = Payment.create(reservation(discount = 30_000), CardType.KB, cardNo)

            assertAll(
                { assertThat(payment.status).isEqualTo(PaymentStatus.CREATED) },
                { assertThat(payment.amount).isEqualTo(Money.krw(70_000)) },
                { assertThat(payment.transactionKey).isNull() },
            )
        }

        @DisplayName("카드 번호 형식이 틀리면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenCardNoInvalid() {
            val exception = assertThrows<CoreException> {
                Payment.create(reservation(), CardType.SAMSUNG, "1234567890123456")
            }
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("주문 ID 변환은,")
    @Nested
    inner class OrderId {
        @DisplayName("예약 ID를 PG 규격(6자리 이상)으로 패딩하고, 다시 예약 ID로 복원할 수 있다.")
        @Test
        fun padsAndRestoresReservationId() {
            assertAll(
                { assertThat(Payment.orderIdOf(7L)).isEqualTo("000007") },
                { assertThat(Payment.orderIdOf(1234567L)).isEqualTo("1234567") },
                { assertThat(Payment.reservationIdOf("000007")).isEqualTo(7L) },
            )
        }
    }

    @DisplayName("상태 전이는,")
    @Nested
    inner class Transition {
        @DisplayName("CREATED → REQUESTED 접수 반영 시 transactionKey 를 보존한다.")
        @Test
        fun marksRequested_withTransactionKey() {
            val payment = payment()

            payment.markRequested("tx-1")

            assertAll(
                { assertThat(payment.status).isEqualTo(PaymentStatus.REQUESTED) },
                { assertThat(payment.transactionKey).isEqualTo("tx-1") },
            )
        }

        @DisplayName("접수 확정 실패는 CREATED 에서만 가능하다 — 이미 접수된 결제에서는 예외가 발생한다.")
        @Test
        fun throwsInvalidState_whenRequestFailedAfterRequested() {
            val payment = payment()
            payment.markRequested("tx-1")

            val exception = assertThrows<CoreException> { payment.markRequestFailed("서버 불안정") }
            assertThat(exception.errorType).isEqualTo(ErrorType.INVALID_PAYMENT_STATE)
        }

        @DisplayName("접수 응답 유실(타임아웃) 대비 — CREATED 에서도 성공/실패 결과를 바로 반영할 수 있다.")
        @Test
        fun allowsResultDirectlyFromCreated() {
            val succeeded = payment()
            succeeded.succeed("tx-1")

            val failed = payment()
            failed.fail("tx-2", "한도초과")

            assertAll(
                { assertThat(succeeded.status).isEqualTo(PaymentStatus.SUCCESS) },
                { assertThat(succeeded.transactionKey).isEqualTo("tx-1") },
                { assertThat(failed.status).isEqualTo(PaymentStatus.FAILED) },
                { assertThat(failed.failureReason).isEqualTo("한도초과") },
            )
        }

        @DisplayName("종결 상태(SUCCESS)에서 다른 전이를 시도하면 INVALID_PAYMENT_STATE 예외가 발생한다.")
        @Test
        fun throwsInvalidState_whenTransitionFromTerminal() {
            val payment = payment()
            payment.succeed("tx-1")

            val exception = assertThrows<CoreException> { payment.fail("tx-1", "한도초과") }
            assertThat(exception.errorType).isEqualTo(ErrorType.INVALID_PAYMENT_STATE)
        }
    }
}
