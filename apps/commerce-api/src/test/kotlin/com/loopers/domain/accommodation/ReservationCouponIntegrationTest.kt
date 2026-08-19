package com.loopers.domain.accommodation

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

@SpringBootTest
class ReservationCouponIntegrationTest @Autowired constructor(
    private val reservationService: ReservationService,
    private val couponService: CouponService,
    private val dailyRoomRateJpaRepository: DailyRoomRateJpaRepository,
    private val dailyRoomInventoryJpaRepository: DailyRoomInventoryJpaRepository,
    private val issuedCouponJpaRepository: IssuedCouponJpaRepository,
    private val reservationJpaRepository: ReservationJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val guestId = 1L
    private val roomTypeId = 100L
    private val checkIn = LocalDate.of(2026, 6, 10)
    private val checkOut = LocalDate.of(2026, 6, 12) // 2박
    private val dates = listOf(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 11))
    private val now: ZonedDateTime = ZonedDateTime.parse("2026-06-01T00:00:00+09:00")
    private val future: ZonedDateTime = now.plusDays(60)

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun seedRates(amount: Long = 50_000) =
        dates.map { dailyRoomRateJpaRepository.save(DailyRoomRate(roomTypeId, it, Money.krw(amount))) }

    private fun seedInventory(remaining: Int = 1) =
        dates.map { dailyRoomInventoryJpaRepository.save(DailyRoomInventory(roomTypeId, it, remaining)) }

    private fun issueFixed(value: Long, userId: Long = guestId) =
        couponService.issue(userId, couponService.createTemplate("정액 ${value}원", CouponType.FIXED, value, null, future).id, now)

    private fun remainingOf(seeded: List<DailyRoomInventory>) =
        seeded.map { dailyRoomInventoryJpaRepository.findById(it.id).get().remaining }

    @DisplayName("쿠폰을 적용해 예약할 때,")
    @Nested
    inner class WithCoupon {
        @DisplayName("성공하면 할인 전/할인/최종 금액 스냅샷을 남기고, 쿠폰은 USED 가 되며, 재고가 차감된다.")
        @Test
        fun success_appliesDiscount_marksUsed_decrementsInventory() {
            seedRates(amount = 50_000) // 총 100_000
            val inventories = seedInventory(remaining = 1)
            val issued = issueFixed(10_000)

            val reservation = reservationService.create(guestId, roomTypeId, DateRange(checkIn, checkOut), issued.id, now)

            val reloadedCoupon = issuedCouponJpaRepository.findById(issued.id).get()
            assertAll(
                { assertThat(reservation.totalAmount).isEqualTo(Money.krw(100_000)) },
                { assertThat(reservation.discountAmount).isEqualTo(Money.krw(10_000)) },
                { assertThat(reservation.finalAmount).isEqualTo(Money.krw(90_000)) },
                { assertThat(reservation.appliedCouponId).isEqualTo(issued.id) },
                { assertThat(reservation.status).isEqualTo(ReservationStatus.PENDING) },
                { assertThat(reloadedCoupon.status).isEqualTo(IssuedCouponStatus.USED) },
                { assertThat(remainingOf(inventories)).containsExactly(0, 0) },
            )
        }

        @DisplayName("쿠폰 미적용(null)이면, 할인 0·최종=총액으로 예약된다.")
        @Test
        fun success_withoutCoupon() {
            seedRates(amount = 50_000)
            seedInventory(remaining = 1)

            val reservation = reservationService.create(guestId, roomTypeId, DateRange(checkIn, checkOut), null, now)

            assertAll(
                { assertThat(reservation.discountAmount).isEqualTo(Money.ZERO_KRW) },
                { assertThat(reservation.finalAmount).isEqualTo(Money.krw(100_000)) },
                { assertThat(reservation.appliedCouponId).isNull() },
            )
        }
    }

    @DisplayName("사용 불가능한 쿠폰으로 예약하면,")
    @Nested
    inner class InvalidCoupon {
        @DisplayName("존재하지 않는 쿠폰이면, 예약은 실패하고 재고는 차감되지 않는다(전체 롤백).")
        @Test
        fun fails_whenCouponNotFound() {
            seedRates()
            val inventories = seedInventory(remaining = 1)

            val exception = assertThrows<CoreException> {
                reservationService.create(guestId, roomTypeId, DateRange(checkIn, checkOut), 999L, now)
            }

            assertAll(
                { assertThat(exception.errorType).isEqualTo(ErrorType.COUPON_NOT_FOUND) },
                { assertThat(remainingOf(inventories)).containsExactly(1, 1) },
                { assertThat(reservationJpaRepository.count()).isEqualTo(0L) },
            )
        }

        @DisplayName("이미 사용된 쿠폰이면, 예약은 실패하고 재고는 보존된다.")
        @Test
        fun fails_whenCouponAlreadyUsed() {
            seedRates()
            val inventories = seedInventory(remaining = 1)
            val issued = issueFixed(10_000)
            couponService.use(guestId, issued.id, Money.krw(100_000), now) // 선사용

            val exception = assertThrows<CoreException> {
                reservationService.create(guestId, roomTypeId, DateRange(checkIn, checkOut), issued.id, now)
            }

            assertAll(
                { assertThat(exception.errorType).isEqualTo(ErrorType.COUPON_ALREADY_USED) },
                { assertThat(remainingOf(inventories)).containsExactly(1, 1) },
            )
        }

        @DisplayName("타 유저 소유 쿠폰이면, 예약은 실패하고 재고는 보존된다.")
        @Test
        fun fails_whenCouponNotOwned() {
            seedRates()
            val inventories = seedInventory(remaining = 1)
            val othersCoupon = issueFixed(10_000, userId = 2L)

            val exception = assertThrows<CoreException> {
                reservationService.create(guestId, roomTypeId, DateRange(checkIn, checkOut), othersCoupon.id, now)
            }

            assertAll(
                { assertThat(exception.errorType).isEqualTo(ErrorType.COUPON_NOT_OWNED) },
                { assertThat(remainingOf(inventories)).containsExactly(1, 1) },
            )
        }

        @DisplayName("만료된 쿠폰이면, 예약은 실패하고 재고는 보존되며 쿠폰은 사용 처리되지 않는다(AVAILABLE 유지).")
        @Test
        fun fails_whenCouponExpired() {
            seedRates()
            val inventories = seedInventory(remaining = 1)
            // 발급 시점(now)엔 유효, 예약 시점(now+2d)엔 만료되는 쿠폰
            val template = couponService.createTemplate("정액 1만원", CouponType.FIXED, 10_000, null, now.plusDays(1))
            val issued = couponService.issue(guestId, template.id, now)

            val exception = assertThrows<CoreException> {
                reservationService.create(guestId, roomTypeId, DateRange(checkIn, checkOut), issued.id, now.plusDays(2))
            }

            val reloadedCoupon = issuedCouponJpaRepository.findById(issued.id).get()
            assertAll(
                { assertThat(exception.errorType).isEqualTo(ErrorType.COUPON_EXPIRED) },
                { assertThat(remainingOf(inventories)).containsExactly(1, 1) },
                { assertThat(reloadedCoupon.status).isEqualTo(IssuedCouponStatus.AVAILABLE) },
            )
        }
    }

    @DisplayName("다일자 재고가 부분만 가능한 상태에서 쿠폰 예약을 하면,")
    @Nested
    inner class PartialInventoryRollback {
        @DisplayName("한 일자라도 재고가 없으면 예약은 실패하고, 쿠폰은 AVAILABLE 로, 모든 일자 재고가 원복된다.")
        @Test
        fun rollsBackEverything_whenAnyDateSoldOut() {
            seedRates()
            // 첫째 날은 재고 1, 둘째 날은 매진(0)
            val inv1 = dailyRoomInventoryJpaRepository.save(DailyRoomInventory(roomTypeId, dates[0], remaining = 1))
            val inv2 = dailyRoomInventoryJpaRepository.save(DailyRoomInventory(roomTypeId, dates[1], remaining = 0))
            val issued = issueFixed(10_000)

            val exception = assertThrows<CoreException> {
                reservationService.create(guestId, roomTypeId, DateRange(checkIn, checkOut), issued.id, now)
            }

            val reloadedCoupon = issuedCouponJpaRepository.findById(issued.id).get()
            assertAll(
                { assertThat(exception.errorType).isEqualTo(ErrorType.OUT_OF_INVENTORY) },
                { assertThat(reloadedCoupon.status).isEqualTo(IssuedCouponStatus.AVAILABLE) },
                { assertThat(dailyRoomInventoryJpaRepository.findById(inv1.id).get().remaining).isEqualTo(1) },
                { assertThat(dailyRoomInventoryJpaRepository.findById(inv2.id).get().remaining).isEqualTo(0) },
                { assertThat(reservationJpaRepository.count()).isEqualTo(0L) },
            )
        }
    }

    @DisplayName("동일 쿠폰으로 여러 기기에서 동시에 예약하면,")
    @Nested
    inner class ConcurrentSameCoupon {
        @DisplayName("쿠폰은 단 한번만 사용되어 예약은 1건만 성공하고, 재고는 1회만 차감된다.")
        @Test
        fun onlyOneReservationSucceeds() {
            seedRates()
            val inventories = seedInventory(remaining = 100) // 재고는 충분 — 쿠폰이 병목
            val issued = issueFixed(10_000)

            val threadCount = 10
            val executor = Executors.newFixedThreadPool(threadCount)
            val latch = CountDownLatch(threadCount)
            val success = AtomicInteger(0)
            val alreadyUsed = AtomicInteger(0)

            repeat(threadCount) {
                executor.submit {
                    try {
                        reservationService.create(guestId, roomTypeId, DateRange(checkIn, checkOut), issued.id, now)
                        success.incrementAndGet()
                    } catch (e: CoreException) {
                        if (e.errorType == ErrorType.COUPON_ALREADY_USED) alreadyUsed.incrementAndGet()
                    } finally {
                        latch.countDown()
                    }
                }
            }
            latch.await(15, TimeUnit.SECONDS)
            executor.shutdown()

            val reloadedCoupon = issuedCouponJpaRepository.findById(issued.id).get()
            assertAll(
                { assertThat(success.get()).isEqualTo(1) },
                { assertThat(alreadyUsed.get()).isEqualTo(threadCount - 1) },
                { assertThat(reloadedCoupon.status).isEqualTo(IssuedCouponStatus.USED) },
                { assertThat(reservationJpaRepository.count()).isEqualTo(1L) },
                // 성공 1건만 각 일자 1씩 차감 → 100 - 1 = 99
                { assertThat(remainingOf(inventories)).containsExactly(99, 99) },
            )
        }
    }

    @DisplayName("체크인~체크아웃이 겹치는 다일자 예약이 동시에 들어오면,")
    @Nested
    inner class ConcurrentOverlappingDates {
        @DisplayName("마지막 1개를 두고 경쟁해도 더블부킹 없이 1건만 성공하고, 모든 일자 정합성이 보장된다.")
        @Test
        fun noDoubleBooking_acrossAllDates() {
            seedRates()
            val inventories = seedInventory(remaining = 1) // 각 일자 마지막 1개

            val threadCount = 10
            val executor = Executors.newFixedThreadPool(threadCount)
            val latch = CountDownLatch(threadCount)
            val success = AtomicInteger(0)
            val outOfInventory = AtomicInteger(0)

            repeat(threadCount) { i ->
                executor.submit {
                    try {
                        // 게스트는 서로 다르게(쿠폰 미적용) — 재고 경합만 검증
                        reservationService.create(i.toLong() + 10, roomTypeId, DateRange(checkIn, checkOut), null, now)
                        success.incrementAndGet()
                    } catch (e: CoreException) {
                        if (e.errorType == ErrorType.OUT_OF_INVENTORY) outOfInventory.incrementAndGet()
                    } finally {
                        latch.countDown()
                    }
                }
            }
            latch.await(15, TimeUnit.SECONDS)
            executor.shutdown()

            assertAll(
                { assertThat(success.get()).isEqualTo(1) },
                { assertThat(outOfInventory.get()).isEqualTo(threadCount - 1) },
                { assertThat(reservationJpaRepository.count()).isEqualTo(1L) },
                { assertThat(remainingOf(inventories)).containsExactly(0, 0) },
            )
        }
    }
}
