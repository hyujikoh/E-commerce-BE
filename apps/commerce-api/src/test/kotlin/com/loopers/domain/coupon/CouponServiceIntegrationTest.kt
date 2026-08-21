package com.loopers.domain.coupon

import com.loopers.domain.accommodation.vo.Money
import com.loopers.infrastructure.coupon.CouponJpaRepository
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
import java.time.ZonedDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
class CouponServiceIntegrationTest @Autowired constructor(
    private val couponService: CouponService,
    private val couponJpaRepository: CouponJpaRepository,
    private val issuedCouponJpaRepository: IssuedCouponJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val userId = 1L
    private val now: ZonedDateTime = ZonedDateTime.parse("2026-06-01T00:00:00+09:00")
    private val future: ZonedDateTime = now.plusDays(30)

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun fixedTemplate(value: Long = 5_000, minOrderAmount: Long? = null, expiredAt: ZonedDateTime = future): Coupon =
        couponService.createTemplate("정액 ${value}원", CouponType.FIXED, value, minOrderAmount, expiredAt)

    @DisplayName("쿠폰을 발급할 때,")
    @Nested
    inner class Issue {
        @DisplayName("유효한 템플릿이면, AVAILABLE 상태로 발급된다.")
        @Test
        fun issuesAvailableCoupon() {
            val template = fixedTemplate()

            val issued = couponService.issue(userId, template.id, now)

            assertAll(
                { assertThat(issued.id).isPositive() },
                { assertThat(issued.userId).isEqualTo(userId) },
                { assertThat(issued.couponId).isEqualTo(template.id) },
                { assertThat(issued.status).isEqualTo(IssuedCouponStatus.AVAILABLE) },
            )
        }

        @DisplayName("존재하지 않는 템플릿이면, COUPON_NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenTemplateMissing() {
            val exception = assertThrows<CoreException> { couponService.issue(userId, 999L, now) }
            assertThat(exception.errorType).isEqualTo(ErrorType.COUPON_NOT_FOUND)
        }

        @DisplayName("이미 만료된 템플릿이면, COUPON_EXPIRED 예외가 발생한다.")
        @Test
        fun throwsExpired_whenTemplateExpired() {
            val template = fixedTemplate(expiredAt = now.minusDays(1))

            val exception = assertThrows<CoreException> { couponService.issue(userId, template.id, now) }
            assertThat(exception.errorType).isEqualTo(ErrorType.COUPON_EXPIRED)
        }

        @DisplayName("동일 사용자가 같은 템플릿을 다시 발급하면, COUPON_ALREADY_ISSUED 예외가 발생한다.")
        @Test
        fun throwsAlreadyIssued_whenDuplicate() {
            val template = fixedTemplate()
            couponService.issue(userId, template.id, now)

            val exception = assertThrows<CoreException> { couponService.issue(userId, template.id, now) }
            assertThat(exception.errorType).isEqualTo(ErrorType.COUPON_ALREADY_ISSUED)
        }
    }

    @DisplayName("내 쿠폰 목록을 조회할 때,")
    @Nested
    inner class GetMyCoupons {
        @DisplayName("AVAILABLE / USED / EXPIRED 표시 상태를 함께 반환한다.")
        @Test
        fun returnsEffectiveStatuses() {
            val available = fixedTemplate(expiredAt = now.plusDays(10))
            val used = fixedTemplate(expiredAt = now.plusDays(10))
            val expiring = fixedTemplate(expiredAt = now.plusDays(1))

            couponService.issue(userId, available.id, now)
            val usedIssued = couponService.issue(userId, used.id, now)
            couponService.use(userId, usedIssued.id, Money.krw(50_000), now)
            couponService.issue(userId, expiring.id, now)

            // 발급 후 시간이 흘러 expiring 템플릿이 만료된 시점에 조회
            val evaluateAt = now.plusDays(2)
            val owned = couponService.getMyCoupons(userId, evaluateAt).associateBy { it.couponId }

            assertAll(
                { assertThat(owned[available.id]?.status).isEqualTo(IssuedCouponStatus.AVAILABLE) },
                { assertThat(owned[used.id]?.status).isEqualTo(IssuedCouponStatus.USED) },
                { assertThat(owned[expiring.id]?.status).isEqualTo(IssuedCouponStatus.EXPIRED) },
            )
        }

        @DisplayName("발급 이력이 없으면, 빈 목록을 반환한다.")
        @Test
        fun returnsEmpty_whenNothingIssued() {
            assertThat(couponService.getMyCoupons(userId, now)).isEmpty()
        }
    }

    @DisplayName("쿠폰을 사용할 때,")
    @Nested
    inner class Use {
        @DisplayName("정상 쿠폰이면, 할인액을 반환하고 상태가 USED 로 바뀐다.")
        @Test
        fun marksUsed_andReturnsDiscount() {
            val template = fixedTemplate(value = 5_000)
            val issued = couponService.issue(userId, template.id, now)

            val discount = couponService.use(userId, issued.id, Money.krw(100_000), now)

            val reloaded = issuedCouponJpaRepository.findById(issued.id).get()
            assertAll(
                { assertThat(discount).isEqualTo(Money.krw(5_000)) },
                { assertThat(reloaded.status).isEqualTo(IssuedCouponStatus.USED) },
                { assertThat(reloaded.usedAt).isNotNull() },
            )
        }

        @DisplayName("존재하지 않는 발급 쿠폰이면, COUPON_NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenMissing() {
            val exception = assertThrows<CoreException> { couponService.use(userId, 999L, Money.krw(100_000), now) }
            assertThat(exception.errorType).isEqualTo(ErrorType.COUPON_NOT_FOUND)
        }

        @DisplayName("타 유저 소유 쿠폰이면, COUPON_NOT_OWNED 예외가 발생한다.")
        @Test
        fun throwsNotOwned_whenOtherUser() {
            val template = fixedTemplate()
            val issued = couponService.issue(userId, template.id, now)

            val exception = assertThrows<CoreException> { couponService.use(2L, issued.id, Money.krw(100_000), now) }
            assertThat(exception.errorType).isEqualTo(ErrorType.COUPON_NOT_OWNED)
        }

        @DisplayName("이미 사용된 쿠폰이면, COUPON_ALREADY_USED 예외가 발생한다.")
        @Test
        fun throwsAlreadyUsed_whenReused() {
            val template = fixedTemplate()
            val issued = couponService.issue(userId, template.id, now)
            couponService.use(userId, issued.id, Money.krw(100_000), now)

            val exception = assertThrows<CoreException> { couponService.use(userId, issued.id, Money.krw(100_000), now) }
            assertThat(exception.errorType).isEqualTo(ErrorType.COUPON_ALREADY_USED)
        }

        @DisplayName("만료된 쿠폰이면, COUPON_EXPIRED 예외가 발생하고 사용 처리되지 않는다.")
        @Test
        fun throwsExpired_andDoesNotMarkUsed() {
            // 발급 시점엔 유효(now+1d), 사용 시점엔 만료(now+2d)
            val template = fixedTemplate(expiredAt = now.plusDays(1))
            val issued = couponService.issue(userId, template.id, now)

            val exception = assertThrows<CoreException> {
                couponService.use(userId, issued.id, Money.krw(100_000), now.plusDays(2))
            }

            val reloaded = issuedCouponJpaRepository.findById(issued.id).get()
            assertAll(
                { assertThat(exception.errorType).isEqualTo(ErrorType.COUPON_EXPIRED) },
                { assertThat(reloaded.status).isEqualTo(IssuedCouponStatus.AVAILABLE) },
            )
        }

        @DisplayName("최소 주문 금액 미달이면, COUPON_MIN_ORDER_NOT_MET 예외가 발생하고 사용 처리되지 않는다.")
        @Test
        fun throwsMinOrderNotMet_andDoesNotMarkUsed() {
            val template = fixedTemplate(minOrderAmount = 100_000)
            val issued = couponService.issue(userId, template.id, now)

            val exception = assertThrows<CoreException> {
                couponService.use(userId, issued.id, Money.krw(50_000), now)
            }

            val reloaded = issuedCouponJpaRepository.findById(issued.id).get()
            assertAll(
                { assertThat(exception.errorType).isEqualTo(ErrorType.COUPON_MIN_ORDER_NOT_MET) },
                { assertThat(reloaded.status).isEqualTo(IssuedCouponStatus.AVAILABLE) },
            )
        }
    }

    @DisplayName("쿠폰을 복구할 때,")
    @Nested
    inner class Restore {
        @DisplayName("사용된 쿠폰이면, AVAILABLE 로 되돌아가고 usedAt 이 초기화된다.")
        @Test
        fun restoresToAvailable_whenUsed() {
            val template = fixedTemplate()
            val issued = couponService.issue(userId, template.id, now)
            couponService.use(userId, issued.id, Money.krw(100_000), now)

            couponService.restore(issued.id)

            val reloaded = issuedCouponJpaRepository.findById(issued.id).get()
            assertAll(
                { assertThat(reloaded.status).isEqualTo(IssuedCouponStatus.AVAILABLE) },
                { assertThat(reloaded.usedAt).isNull() },
            )
        }

        @DisplayName("복구된 쿠폰은 다시 사용할 수 있다.")
        @Test
        fun canUseAgain_afterRestore() {
            val template = fixedTemplate(value = 5_000)
            val issued = couponService.issue(userId, template.id, now)
            couponService.use(userId, issued.id, Money.krw(100_000), now)
            couponService.restore(issued.id)

            val discount = couponService.use(userId, issued.id, Money.krw(100_000), now)

            assertThat(discount).isEqualTo(Money.krw(5_000))
        }

        @DisplayName("사용되지 않은 쿠폰이면, 아무 일도 일어나지 않는다(멱등).")
        @Test
        fun doesNothing_whenNotUsed() {
            val template = fixedTemplate()
            val issued = couponService.issue(userId, template.id, now)

            couponService.restore(issued.id)

            val reloaded = issuedCouponJpaRepository.findById(issued.id).get()
            assertThat(reloaded.status).isEqualTo(IssuedCouponStatus.AVAILABLE)
        }
    }

    @DisplayName("동일 쿠폰으로 여러 요청이 동시에 사용하면,")
    @Nested
    inner class ConcurrentUse {
        @DisplayName("정확히 1건만 성공하고 나머지는 COUPON_ALREADY_USED 가 되며, 쿠폰은 단 한번만 사용된다.")
        @Test
        fun onlyOneSucceeds() {
            val template = fixedTemplate()
            val issued = couponService.issue(userId, template.id, now)

            val threadCount = 10
            val executor = Executors.newFixedThreadPool(threadCount)
            val latch = CountDownLatch(threadCount)
            val success = AtomicInteger(0)
            val alreadyUsed = AtomicInteger(0)

            repeat(threadCount) {
                executor.submit {
                    try {
                        couponService.use(userId, issued.id, Money.krw(100_000), now)
                        success.incrementAndGet()
                    } catch (e: CoreException) {
                        if (e.errorType == ErrorType.COUPON_ALREADY_USED) alreadyUsed.incrementAndGet()
                    } finally {
                        latch.countDown()
                    }
                }
            }
            latch.await(10, TimeUnit.SECONDS)
            executor.shutdown()

            val reloaded = issuedCouponJpaRepository.findById(issued.id).get()
            assertAll(
                { assertThat(success.get()).isEqualTo(1) },
                { assertThat(alreadyUsed.get()).isEqualTo(threadCount - 1) },
                { assertThat(reloaded.status).isEqualTo(IssuedCouponStatus.USED) },
            )
        }
    }

    @DisplayName("동일 사용자가 같은 템플릿을 동시에 발급 요청하면,")
    @Nested
    inner class ConcurrentIssue {
        @DisplayName("정확히 1건만 발급되고 나머지는 모두 COUPON_ALREADY_ISSUED 가 된다(UNIQUE 위반도 도메인 에러로 변환).")
        @Test
        fun onlyOneIssued() {
            val template = fixedTemplate()

            val threadCount = 10
            val executor = Executors.newFixedThreadPool(threadCount)
            val latch = CountDownLatch(threadCount)
            val success = AtomicInteger(0)
            val alreadyIssued = AtomicInteger(0)

            repeat(threadCount) {
                executor.submit {
                    try {
                        couponService.issue(userId, template.id, now)
                        success.incrementAndGet()
                    } catch (e: CoreException) {
                        // 사전 검증 실패와 UNIQUE 위반 모두 COUPON_ALREADY_ISSUED 로 일관되게 도착해야 한다.
                        if (e.errorType == ErrorType.COUPON_ALREADY_ISSUED) alreadyIssued.incrementAndGet()
                    } finally {
                        latch.countDown()
                    }
                }
            }
            latch.await(10, TimeUnit.SECONDS)
            executor.shutdown()

            val issuedCount = issuedCouponJpaRepository.findAllByUserId(userId).count { it.couponId == template.id }
            assertAll(
                { assertThat(success.get()).isEqualTo(1) },
                { assertThat(alreadyIssued.get()).isEqualTo(threadCount - 1) },
                { assertThat(issuedCount).isEqualTo(1) },
            )
        }
    }
}
