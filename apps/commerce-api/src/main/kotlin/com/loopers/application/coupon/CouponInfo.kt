package com.loopers.application.coupon

import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponType
import java.time.ZonedDateTime

/** 쿠폰 템플릿 정보(admin 응답용). */
data class CouponInfo(
    val id: Long,
    val name: String,
    val type: CouponType,
    val value: Long,
    val minOrderAmount: Long?,
    val expiredAt: ZonedDateTime,
) {
    companion object {
        fun from(coupon: Coupon): CouponInfo =
            CouponInfo(
                id = coupon.id,
                name = coupon.name,
                type = coupon.type,
                value = coupon.value,
                minOrderAmount = coupon.minOrderAmount,
                expiredAt = coupon.expiredAt,
            )
    }
}
