package com.loopers.domain.accommodation

import com.loopers.application.accommodation.ReservationFacade
import com.loopers.domain.accommodation.vo.DateRange
import com.loopers.domain.accommodation.vo.Money
import com.loopers.domain.coupon.CouponService
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.IssuedCouponStatus
import com.loopers.infrastructure.accommodation.DailyRoomInventoryJpaRepository
import com.loopers.infrastructure.accommodation.DailyRoomRateJpaRepository
import com.loopers.infrastructure.accommodation.ReservationJpaRepository
import com.loopers.infrastructure.coupon.IssuedCouponJpaRepository
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
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 예약 확정/취소/만료 슬라이스 통합 테스트.
 * 만료 스케줄러는 백그라운드 실행이 시드 데이터를 건드리지 않도록 프로퍼티로 끈다.
 */
@SpringBootTest(properties = ["scheduler.reservation-expiration.enabled=false"])
class ReservationLifecycleIntegrationTest @Autowired constructor(
    private val reservationService: ReservationService,
    private val reservationFacade: ReservationFacade,
    private val couponService: CouponService,
    private val reservationJpaRepository: ReservationJpaRepository,
    private val dailyRoomRateJpaRepository: DailyRoomRateJpaRepository,
    private val dailyRoomInventoryJpaRepository: DailyRoomInventoryJpaRepository,
    private val issuedCouponJpaRepository: IssuedCouponJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val guestId = 1L
    private val roomTypeId = 100L
    private val checkIn = LocalDate.of(2026, 6, 10)
    private val checkOut = LocalDate.of(2026, 6, 12) // 2박
    private val dates = listOf(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 11))

    /** 만료 시각(now+10분)이 이미 지난 예약을 만들기 위한 과거 시점. */
    private val overdue: ZonedDateTime get() = ZonedDateTime.now().minusMinutes(11)

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun seedRatesAndInventory(remaining: Int = 1) {
        dates.forEach { date ->
            dailyRoomRateJpaRepository.save(DailyRoomRate(roomTypeId, date, Money.krw(50_000)))
            dailyRoomInventoryJpaRepository.save(DailyRoomInventory(roomTypeId, date, remaining))
        }
    }

    private fun issueCoupon(): Long {
        val template = couponService.createTemplate("정액 5천원", CouponType.FIXED, 5_000, null, ZonedDateTime.now().plusDays(30))
        return couponService.issue(guestId, template.id).id
    }

    private fun createReservation(couponId: Long? = null, now: ZonedDateTime = ZonedDateTime.now()): Reservation =
        reservationService.create(guestId, roomTypeId, DateRange(checkIn, checkOut), couponId, now)

    private fun remainingByDate(): List<Int> =
        dates.map { date -> dailyRoomInventoryJpaRepository.findAll().first { it.roomTypeId == roomTypeId && it.date == date }.remaining }

    @DisplayName("예약을 확정할 때,")
    @Nested
    inner class Confirm {
        @DisplayName("PENDING 예약이면, CONFIRMED 로 전이된다.")
        @Test
        fun confirmsPendingReservation() {
            // arrange
            seedRatesAndInventory()
            val reservation = createReservation()

            // act
            val info = reservationFacade.confirm(reservation.id)

            // assert
            assertAll(
                { assertThat(info.status).isEqualTo(ReservationStatus.CONFIRMED) },
                { assertThat(reservationJpaRepository.findById(reservation.id).get().status).isEqualTo(ReservationStatus.CONFIRMED) },
            )
        }

        @DisplayName("홀드가 만료된 PENDING 이면, 취소 정리(재고·쿠폰 복구)를 커밋한 뒤 INVALID_RESERVATION_STATE 로 실패한다.")
        @Test
        fun cleansUpAndThrows_whenExpired() {
            // arrange — 만료된 예약 + 쿠폰 사용
            seedRatesAndInventory(remaining = 1)
            val couponId = issueCoupon()
            val reservation = createReservation(couponId = couponId, now = overdue)

            // act
            val exception = assertThrows<CoreException> { reservationFacade.confirm(reservation.id) }

            // assert — 실패했지만 만료 정리는 커밋되어 있어야 한다
            val reloaded = reservationJpaRepository.findById(reservation.id).get()
            val coupon = issuedCouponJpaRepository.findById(couponId).get()
            assertAll(
                { assertThat(exception.errorType).isEqualTo(ErrorType.INVALID_RESERVATION_STATE) },
                { assertThat(reloaded.status).isEqualTo(ReservationStatus.CANCELLED) },
                { assertThat(reloaded.cancelReason).isEqualTo(CancelReason.EXPIRED) },
                { assertThat(remainingByDate()).containsExactly(1, 1) },
                { assertThat(coupon.status).isEqualTo(IssuedCouponStatus.AVAILABLE) },
            )
        }

        @DisplayName("이미 취소된 예약이면, INVALID_RESERVATION_STATE 예외가 발생한다.")
        @Test
        fun throwsInvalidState_whenAlreadyCancelled() {
            // arrange
            seedRatesAndInventory()
            val reservation = createReservation()
            reservationFacade.cancel(reservation.id, guestId)

            // act & assert
            val exception = assertThrows<CoreException> { reservationFacade.confirm(reservation.id) }
            assertThat(exception.errorType).isEqualTo(ErrorType.INVALID_RESERVATION_STATE)
        }

        @DisplayName("존재하지 않는 예약이면, RESERVATION_NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenMissing() {
            val exception = assertThrows<CoreException> { reservationFacade.confirm(999L) }
            assertThat(exception.errorType).isEqualTo(ErrorType.RESERVATION_NOT_FOUND)
        }
    }

    @DisplayName("예약을 취소할 때,")
    @Nested
    inner class Cancel {
        @DisplayName("PENDING 예약이면, USER_REQUEST 사유로 취소되고 재고와 쿠폰이 복구된다.")
        @Test
        fun cancelsPending_andRestoresInventoryAndCoupon() {
            // arrange
            seedRatesAndInventory(remaining = 1)
            val couponId = issueCoupon()
            val reservation = createReservation(couponId = couponId)

            // act
            val info = reservationFacade.cancel(reservation.id, guestId)

            // assert
            val coupon = issuedCouponJpaRepository.findById(couponId).get()
            assertAll(
                { assertThat(info.status).isEqualTo(ReservationStatus.CANCELLED) },
                { assertThat(info.cancelReason).isEqualTo(CancelReason.USER_REQUEST) },
                { assertThat(info.canceledAt).isNotNull() },
                { assertThat(remainingByDate()).containsExactly(1, 1) },
                { assertThat(coupon.status).isEqualTo(IssuedCouponStatus.AVAILABLE) },
                { assertThat(coupon.usedAt).isNull() },
            )
        }

        @DisplayName("CONFIRMED 예약이면, 재고는 복구되지만 쿠폰은 USED 로 유지된다(환불 정책은 후속 도메인).")
        @Test
        fun cancelsConfirmed_restoresInventory_keepsCouponUsed() {
            // arrange — 결제 완료된 예약
            seedRatesAndInventory(remaining = 1)
            val couponId = issueCoupon()
            val reservation = createReservation(couponId = couponId)
            reservationFacade.confirm(reservation.id)

            // act
            val info = reservationFacade.cancel(reservation.id, guestId)

            // assert
            val coupon = issuedCouponJpaRepository.findById(couponId).get()
            assertAll(
                { assertThat(info.status).isEqualTo(ReservationStatus.CANCELLED) },
                { assertThat(info.cancelReason).isEqualTo(CancelReason.USER_REQUEST) },
                { assertThat(remainingByDate()).containsExactly(1, 1) },
                { assertThat(coupon.status).isEqualTo(IssuedCouponStatus.USED) },
            )
        }

        @DisplayName("홀드가 만료된 PENDING 이면, 사용자 요청이어도 EXPIRED 사유로 정리된다(정산 분기 키 보존).")
        @Test
        fun recordsExpiredReason_whenCancelingOverduePending() {
            // arrange
            seedRatesAndInventory(remaining = 1)
            val couponId = issueCoupon()
            val reservation = createReservation(couponId = couponId, now = overdue)

            // act
            val info = reservationFacade.cancel(reservation.id, guestId)

            // assert
            val coupon = issuedCouponJpaRepository.findById(couponId).get()
            assertAll(
                { assertThat(info.status).isEqualTo(ReservationStatus.CANCELLED) },
                { assertThat(info.cancelReason).isEqualTo(CancelReason.EXPIRED) },
                { assertThat(remainingByDate()).containsExactly(1, 1) },
                { assertThat(coupon.status).isEqualTo(IssuedCouponStatus.AVAILABLE) },
            )
        }

        @DisplayName("본인 예약이 아니면, RESERVATION_NOT_OWNED 예외가 발생한다.")
        @Test
        fun throwsNotOwned_whenOtherGuest() {
            // arrange
            seedRatesAndInventory()
            val reservation = createReservation()

            // act & assert
            val exception = assertThrows<CoreException> { reservationFacade.cancel(reservation.id, guestId = 2L) }
            assertThat(exception.errorType).isEqualTo(ErrorType.RESERVATION_NOT_OWNED)
        }

        @DisplayName("존재하지 않는 예약이면, RESERVATION_NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenMissing() {
            val exception = assertThrows<CoreException> { reservationFacade.cancel(999L, guestId) }
            assertThat(exception.errorType).isEqualTo(ErrorType.RESERVATION_NOT_FOUND)
        }

        @DisplayName("체크인 이후에는 취소할 수 없고(D-3), 재고도 복구되지 않는다.")
        @Test
        fun throwsInvalidState_whenCheckedIn() {
            // arrange — 체크인 상태까지 전이
            seedRatesAndInventory(remaining = 1)
            val reservation = createReservation()
            reservationJpaRepository.findById(reservation.id).get().also {
                it.confirm()
                it.checkIn()
                reservationJpaRepository.save(it)
            }

            // act & assert
            val exception = assertThrows<CoreException> { reservationFacade.cancel(reservation.id, guestId) }
            assertAll(
                { assertThat(exception.errorType).isEqualTo(ErrorType.INVALID_RESERVATION_STATE) },
                { assertThat(remainingByDate()).containsExactly(0, 0) },
            )
        }
    }

    @DisplayName("예약을 만료 처리할 때,")
    @Nested
    inner class Expire {
        @DisplayName("만료 시각이 지난 PENDING 이면, EXPIRED 사유로 취소되고 재고와 쿠폰이 복구된다.")
        @Test
        fun expiresOverduePending_andRestores() {
            // arrange
            seedRatesAndInventory(remaining = 1)
            val couponId = issueCoupon()
            val reservation = createReservation(couponId = couponId, now = overdue)

            // act
            val expired = reservationService.expire(reservation.id)

            // assert
            val reloaded = reservationJpaRepository.findById(reservation.id).get()
            val coupon = issuedCouponJpaRepository.findById(couponId).get()
            assertAll(
                { assertThat(expired).isTrue() },
                { assertThat(reloaded.status).isEqualTo(ReservationStatus.CANCELLED) },
                { assertThat(reloaded.cancelReason).isEqualTo(CancelReason.EXPIRED) },
                { assertThat(remainingByDate()).containsExactly(1, 1) },
                { assertThat(coupon.status).isEqualTo(IssuedCouponStatus.AVAILABLE) },
            )
        }

        @DisplayName("만료 시각이 지나지 않은 PENDING 이면, 아무것도 바꾸지 않고 false 를 반환한다.")
        @Test
        fun returnsFalse_whenNotDue() {
            // arrange
            seedRatesAndInventory(remaining = 1)
            val reservation = createReservation()

            // act
            val expired = reservationService.expire(reservation.id)

            // assert
            val reloaded = reservationJpaRepository.findById(reservation.id).get()
            assertAll(
                { assertThat(expired).isFalse() },
                { assertThat(reloaded.status).isEqualTo(ReservationStatus.PENDING) },
                { assertThat(remainingByDate()).containsExactly(0, 0) },
            )
        }

        @DisplayName("CONFIRMED 예약이면, 만료 시각이 지났어도 false 를 반환한다.")
        @Test
        fun returnsFalse_whenConfirmed() {
            // arrange — 만료 시각은 지났지만 이미 확정된 예약 (만료 검사 없는 service.confirm 으로 상태 구성)
            seedRatesAndInventory()
            val reservation = createReservation(now = overdue)
            reservationService.confirm(reservation.id)

            // act
            val expired = reservationService.expire(reservation.id)

            // assert
            assertAll(
                { assertThat(expired).isFalse() },
                { assertThat(reservationJpaRepository.findById(reservation.id).get().status).isEqualTo(ReservationStatus.CONFIRMED) },
            )
        }

        @DisplayName("존재하지 않는 예약이면, false 를 반환한다.")
        @Test
        fun returnsFalse_whenMissing() {
            assertThat(reservationService.expire(999L)).isFalse()
        }
    }

    @DisplayName("만료 예약을 일괄 정리할 때,")
    @Nested
    inner class ExpireOverdue {
        @DisplayName("만료 시각이 지난 PENDING 만 골라 취소하고 처리 건수를 반환한다.")
        @Test
        fun expiresOnlyOverduePendings() {
            // arrange — 만료 2건(게스트 1·2) + 미만료 1건(게스트 3). 재고는 3건 수용
            seedRatesAndInventory(remaining = 3)
            val overdue1 = reservationService.create(1L, roomTypeId, DateRange(checkIn, checkOut), null, overdue)
            val overdue2 = reservationService.create(2L, roomTypeId, DateRange(checkIn, checkOut), null, overdue)
            val active = reservationService.create(3L, roomTypeId, DateRange(checkIn, checkOut))

            // act
            val count = reservationService.expireOverdue()

            // assert — 만료 2건만 취소, 재고는 2건 몫만 복구(3-3+2=2)
            assertAll(
                { assertThat(count).isEqualTo(2) },
                { assertThat(reservationJpaRepository.findById(overdue1.id).get().status).isEqualTo(ReservationStatus.CANCELLED) },
                { assertThat(reservationJpaRepository.findById(overdue2.id).get().status).isEqualTo(ReservationStatus.CANCELLED) },
                { assertThat(reservationJpaRepository.findById(active.id).get().status).isEqualTo(ReservationStatus.PENDING) },
                { assertThat(remainingByDate()).containsExactly(2, 2) },
            )
        }

        @DisplayName("limit 을 초과하는 만료 건은 이번 회차에서 처리하지 않는다.")
        @Test
        fun respectsLimit() {
            // arrange — 만료 3건
            seedRatesAndInventory(remaining = 3)
            (1L..3L).forEach { reservationService.create(it, roomTypeId, DateRange(checkIn, checkOut), null, overdue) }

            // act
            val count = reservationService.expireOverdue(limit = 2)

            // assert
            assertThat(count).isEqualTo(2)
        }
    }

    @DisplayName("같은 만료 예약을 여러 경로가 동시에 만료 처리하면,")
    @Nested
    inner class ConcurrentExpire {
        @DisplayName("정확히 1건만 성공하고, 재고는 정확히 한 번만 복구된다.")
        @Test
        fun onlyOneExpires_whenRacing() {
            // arrange — 만료 예약 1건 (재고 1 → 0)
            seedRatesAndInventory(remaining = 1)
            val reservation = createReservation(now = overdue)
            val threadCount = 5
            val executor = Executors.newFixedThreadPool(threadCount)
            val latch = CountDownLatch(threadCount)
            val success = AtomicInteger(0)

            // act — 동시에 만료 처리 시도 (스케줄러 다중 인스턴스 + 결제 재진입 경쟁 시뮬레이션)
            repeat(threadCount) {
                executor.submit {
                    try {
                        if (reservationService.expire(reservation.id)) success.incrementAndGet()
                    } finally {
                        latch.countDown()
                    }
                }
            }
            latch.await(10, TimeUnit.SECONDS)
            executor.shutdown()

            // assert — 한 번만 만료 처리되어 재고가 정확히 +1 (중복 복구 시 2 이상이 된다)
            assertAll(
                { assertThat(success.get()).isEqualTo(1) },
                { assertThat(reservationJpaRepository.findById(reservation.id).get().status).isEqualTo(ReservationStatus.CANCELLED) },
                { assertThat(remainingByDate()).containsExactly(1, 1) },
            )
        }
    }
}
