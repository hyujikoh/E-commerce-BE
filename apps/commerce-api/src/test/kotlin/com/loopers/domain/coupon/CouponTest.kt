package com.loopers.domain.coupon

import com.loopers.domain.accommodation.vo.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.ZonedDateTime

class CouponTest {
    private val now: ZonedDateTime = ZonedDateTime.parse("2026-06-01T00:00:00+09:00")
    private val future: ZonedDateTime = now.plusDays(30)
    private val past: ZonedDateTime = now.minusDays(1)

    @DisplayName("쿠폰 템플릿을 생성할 때,")
    @Nested
    inner class Create {
        @DisplayName("이름이 비어 있으면, INVALID_COUPON 예외가 발생한다.")
        @Test
        fun throwsInvalidCoupon_whenNameBlank() {
            val exception = assertThrows<CoreException> {
                Coupon.create("  ", CouponType.FIXED, 1_000, null, future)
            }
            assertThat(exception.errorType).isEqualTo(ErrorType.INVALID_COUPON)
        }

        @DisplayName("값이 0 이하이면, INVALID_COUPON 예외가 발생한다.")
        @Test
        fun throwsInvalidCoupon_whenValueNotPositive() {
            val exception = assertThrows<CoreException> {
                Coupon.create("쿠폰", CouponType.FIXED, 0, null, future)
            }
            assertThat(exception.errorType).isEqualTo(ErrorType.INVALID_COUPON)
        }

        @DisplayName("정률 쿠폰의 비율이 1~100 범위를 벗어나면, INVALID_COUPON 예외가 발생한다.")
        @Test
        fun throwsInvalidCoupon_whenRateOutOfRange() {
            val exception = assertThrows<CoreException> {
                Coupon.create("쿠폰", CouponType.RATE, 101, null, future)
            }
            assertThat(exception.errorType).isEqualTo(ErrorType.INVALID_COUPON)
        }
    }

    @DisplayName("정액(FIXED) 쿠폰의 할인액을 계산할 때,")
    @Nested
    inner class FixedDiscount {
        @DisplayName("주문 금액보다 할인 금액이 작으면, 할인 금액만큼 할인한다.")
        @Test
        fun discountsByFixedValue() {
            val coupon = Coupon.create("5천원 할인", CouponType.FIXED, 5_000, null, future)

            val discount = coupon.calculateDiscount(Money.krw(100_000), now)

            assertThat(discount).isEqualTo(Money.krw(5_000))
        }

        @DisplayName("할인 금액이 주문 금액보다 크면, 주문 금액을 상한으로 할인한다(음수 방지).")
        @Test
        fun capsDiscountAtOrderAmount() {
            val coupon = Coupon.create("10만원 할인", CouponType.FIXED, 100_000, null, future)

            val discount = coupon.calculateDiscount(Money.krw(30_000), now)

            assertThat(discount).isEqualTo(Money.krw(30_000))
        }
    }

    @DisplayName("정률(RATE) 쿠폰의 할인액을 계산할 때,")
    @Nested
    inner class RateDiscount {
        @DisplayName("비율만큼 할인한다.")
        @Test
        fun discountsByRate() {
            val coupon = Coupon.create("10% 할인", CouponType.RATE, 10, null, future)

            val discount = coupon.calculateDiscount(Money.krw(100_000), now)

            assertThat(discount).isEqualTo(Money.krw(10_000))
        }

        @DisplayName("나누어 떨어지지 않으면, 원 단위로 내림한다.")
        @Test
        fun floorsToWon() {
            val coupon = Coupon.create("10% 할인", CouponType.RATE, 10, null, future)

            // 33_333 * 10 / 100 = 3_333.3 → 3_333
            val discount = coupon.calculateDiscount(Money.krw(33_333), now)

            assertThat(discount).isEqualTo(Money.krw(3_333))
        }
    }

    @DisplayName("할인액 계산 검증 단계에서,")
    @Nested
    inner class Validation {
        @DisplayName("만료된 쿠폰이면, COUPON_EXPIRED 예외가 발생한다.")
        @Test
        fun throwsCouponExpired_whenExpired() {
            val coupon = Coupon.create("만료 쿠폰", CouponType.FIXED, 5_000, null, past)

            val exception = assertThrows<CoreException> {
                coupon.calculateDiscount(Money.krw(100_000), now)
            }
            assertThat(exception.errorType).isEqualTo(ErrorType.COUPON_EXPIRED)
        }

        @DisplayName("최소 주문 금액에 미달하면, COUPON_MIN_ORDER_NOT_MET 예외가 발생한다.")
        @Test
        fun throwsMinOrderNotMet_whenBelowMinimum() {
            val coupon = Coupon.create("최소주문 쿠폰", CouponType.FIXED, 5_000, 100_000, future)

            val exception = assertThrows<CoreException> {
                coupon.calculateDiscount(Money.krw(99_999), now)
            }
            assertThat(exception.errorType).isEqualTo(ErrorType.COUPON_MIN_ORDER_NOT_MET)
        }

        @DisplayName("최소 주문 금액을 정확히 충족하면(경계), 정상 할인한다.")
        @Test
        fun discounts_whenExactlyAtMinimum() {
            val coupon = Coupon.create("최소주문 쿠폰", CouponType.FIXED, 5_000, 100_000, future)

            val discount = coupon.calculateDiscount(Money.krw(100_000), now)

            assertThat(discount).isEqualTo(Money.krw(5_000))
        }
    }

    @DisplayName("만료 여부를 판단할 때,")
    @Nested
    inner class IsExpired {
        @DisplayName("만료 시각 이후면 true, 이전이면 false 이다.")
        @Test
        fun returnsExpiry() {
            val coupon = Coupon.create("쿠폰", CouponType.FIXED, 5_000, null, now)

            assertThat(coupon.isExpired(now.plusSeconds(1))).isTrue()
            assertThat(coupon.isExpired(now.minusSeconds(1))).isFalse()
        }
    }
}
