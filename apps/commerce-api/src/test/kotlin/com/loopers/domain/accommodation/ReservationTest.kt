package com.loopers.domain.accommodation

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
import java.time.ZoneId
import java.time.ZonedDateTime

class ReservationTest {
    private val now = ZonedDateTime.of(2026, 6, 1, 12, 0, 0, 0, ZoneId.of("Asia/Seoul"))

    private fun reservation(
        stay: DateRange = DateRange(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 12)),
        nightly: List<ReservationNightly> = listOf(
            ReservationNightly(LocalDate.of(2026, 6, 10), Money.krw(10_000)),
            ReservationNightly(LocalDate.of(2026, 6, 11), Money.krw(20_000)),
        ),
    ): Reservation = Reservation.create(
        guestId = 1L,
        roomTypeId = 100L,
        stayPeriod = stay,
        nightlySnapshots = nightly,
        holdDuration = Duration.ofMinutes(10),
        now = now,
    )

    @DisplayName("예약을 생성할 때,")
    @Nested
    inner class Create {
        @DisplayName("PENDING 상태로 생성되고, 총액은 스냅샷의 합이며, 만료시각은 now+홀드시간이다.")
        @Test
        fun createsPendingReservation() {
            val reservation = reservation()

            assertAll(
                { assertThat(reservation.status).isEqualTo(ReservationStatus.PENDING) },
                { assertThat(reservation.totalAmount).isEqualTo(Money.krw(30_000)) },
                { assertThat(reservation.nightly).hasSize(2) },
                { assertThat(reservation.expiresAt).isEqualTo(now.plusMinutes(10)) },
            )
        }

        @DisplayName("스냅샷 일수가 투숙 일수와 다르면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNightlyCountMismatch() {
            // 투숙 3박인데 가격 스냅샷은 1박만 제공
            val exception = assertThrows<CoreException> {
                reservation(
                    stay = DateRange(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 13)),
                    nightly = listOf(ReservationNightly(LocalDate.of(2026, 6, 10), Money.krw(10_000))),
                )
            }

            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("상태를 전이할 때,")
    @Nested
    inner class Transition {
        @DisplayName("PENDING → confirm() → CONFIRMED 로 전이한다.")
        @Test
        fun confirm() {
            val reservation = reservation().apply { confirm() }

            assertThat(reservation.status).isEqualTo(ReservationStatus.CONFIRMED)
        }

        @DisplayName("PENDING 에서 cancel 하면 CANCELLED + 사유·취소시각이 기록된다.")
        @Test
        fun cancelFromPending() {
            val reservation = reservation().apply { cancel(CancelReason.USER_REQUEST, now) }

            assertAll(
                { assertThat(reservation.status).isEqualTo(ReservationStatus.CANCELLED) },
                { assertThat(reservation.cancelReason).isEqualTo(CancelReason.USER_REQUEST) },
                { assertThat(reservation.canceledAt).isEqualTo(now) },
            )
        }

        @DisplayName("PENDING 에서 expire 하면 CANCELLED + EXPIRED 사유로 기록된다.")
        @Test
        fun expireFromPending() {
            val reservation = reservation().apply { expire(now) }

            assertAll(
                { assertThat(reservation.status).isEqualTo(ReservationStatus.CANCELLED) },
                { assertThat(reservation.cancelReason).isEqualTo(CancelReason.EXPIRED) },
            )
        }

        @DisplayName("PENDING 에서 곧바로 checkIn 하면, INVALID_RESERVATION_STATE 예외가 발생한다.")
        @Test
        fun throwsInvalidState_whenCheckInFromPending() {
            val exception = assertThrows<CoreException> { reservation().checkIn() }

            assertThat(exception.errorType).isEqualTo(ErrorType.INVALID_RESERVATION_STATE)
        }

        @DisplayName("체크인 이후에는 취소할 수 없다(INVALID_RESERVATION_STATE).")
        @Test
        fun throwsInvalidState_whenCancelAfterCheckIn() {
            val reservation = reservation().apply {
                confirm()
                checkIn()
            }

            val exception = assertThrows<CoreException> { reservation.cancel(CancelReason.USER_REQUEST) }

            assertThat(exception.errorType).isEqualTo(ErrorType.INVALID_RESERVATION_STATE)
        }
    }
}
