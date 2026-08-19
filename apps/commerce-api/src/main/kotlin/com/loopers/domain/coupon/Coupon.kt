package com.loopers.domain.coupon

import com.loopers.domain.BaseEntity
import com.loopers.domain.accommodation.vo.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.ZonedDateTime

/**
 * 쿠폰 템플릿. admin 이 등록하며 사용자에게 발급된다(IssuedCoupon).
 *
 * 할인 계산(정액/정률)과 유효성(값 범위, 만료, 최소 주문 금액)은 이 엔티티가 책임진다(도메인 응집).
 * 삭제는 발급 이력(IssuedCoupon)이 참조하므로 소프트 삭제(BaseEntity.delete)로 처리하고 조회에서 제외한다.
 */
@Entity
@Table(name = "coupon")
class Coupon private constructor(
    name: String,
    type: CouponType,
    value: Long,
    minOrderAmount: Long?,
    expiredAt: ZonedDateTime,
) : BaseEntity() {
    @Column(name = "name", nullable = false)
    var name: String = name
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    var type: CouponType = type
        protected set

    /** 정액이면 할인 금액(원), 정률이면 비율(%). */
    @Column(name = "discount_value", nullable = false)
    var value: Long = value
        protected set

    @Column(name = "min_order_amount")
    var minOrderAmount: Long? = minOrderAmount
        protected set

    @Column(name = "expired_at", nullable = false)
    var expiredAt: ZonedDateTime = expiredAt
        protected set

    fun isExpired(now: ZonedDateTime = ZonedDateTime.now()): Boolean = now.isAfter(expiredAt)

    /**
     * 주문 금액에 대한 할인액을 계산한다.
     * - 만료 시 COUPON_EXPIRED
     * - 최소 주문 금액 미달 시 COUPON_MIN_ORDER_NOT_MET
     * - FIXED: min(정액, 주문금액)  /  RATE: floor(주문금액 * 율 / 100)
     *
     * FIXED 는 주문금액으로 상한을 두고, RATE 는 율이 1~100 으로 보장되므로 할인액은 주문금액을 넘지 않는다.
     */
    fun calculateDiscount(orderAmount: Money, now: ZonedDateTime = ZonedDateTime.now()): Money {
        if (isExpired(now)) {
            throw CoreException(ErrorType.COUPON_EXPIRED)
        }
        minOrderAmount?.let { min ->
            if (orderAmount.amount < min) {
                throw CoreException(ErrorType.COUPON_MIN_ORDER_NOT_MET, "최소 주문 금액($min)을 충족하지 않습니다.")
            }
        }
        val discount = when (type) {
            CouponType.FIXED -> minOf(value, orderAmount.amount)
            CouponType.RATE -> orderAmount.amount * value / 100
        }
        return Money(discount, orderAmount.currency)
    }

    fun update(name: String, type: CouponType, value: Long, minOrderAmount: Long?, expiredAt: ZonedDateTime) {
        validate(name, type, value, minOrderAmount)
        this.name = name
        this.type = type
        this.value = value
        this.minOrderAmount = minOrderAmount
        this.expiredAt = expiredAt
    }

    companion object {
        fun create(name: String, type: CouponType, value: Long, minOrderAmount: Long?, expiredAt: ZonedDateTime): Coupon {
            validate(name, type, value, minOrderAmount)
            return Coupon(name, type, value, minOrderAmount, expiredAt)
        }

        private fun validate(name: String, type: CouponType, value: Long, minOrderAmount: Long?) {
            if (name.isBlank()) {
                throw CoreException(ErrorType.INVALID_COUPON, "쿠폰 이름은 비어 있을 수 없습니다.")
            }
            if (value <= 0) {
                throw CoreException(ErrorType.INVALID_COUPON, "쿠폰 값은 0보다 커야 합니다.")
            }
            if (type == CouponType.RATE && value !in 1..100) {
                throw CoreException(ErrorType.INVALID_COUPON, "정률 쿠폰의 비율은 1~100 사이여야 합니다.")
            }
            minOrderAmount?.let {
                if (it < 0) {
                    throw CoreException(ErrorType.INVALID_COUPON, "최소 주문 금액은 음수일 수 없습니다.")
                }
            }
        }
    }
}
