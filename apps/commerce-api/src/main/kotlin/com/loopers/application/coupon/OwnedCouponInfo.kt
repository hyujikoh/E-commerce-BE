package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.IssuedCouponStatus
import com.loopers.domain.coupon.OwnedCoupon
import java.time.ZonedDateTime

/** 사용자 보유 쿠폰 정보(내 쿠폰 목록용). status 는 표시용 유효 상태(AVAILABLE/USED/EXPIRED). */
data class OwnedCouponInfo(
    val issuedCouponId: Long,
    val couponId: Long,
    val name: String,
    val type: CouponType,
    val value: Long,
    val minOrderAmount: Long?,
    val expiredAt: ZonedDateTime,
    val status: IssuedCouponStatus,
    val usedAt: ZonedDateTime?,
) {
    companion object {
        fun from(owned: OwnedCoupon): OwnedCouponInfo =
            OwnedCouponInfo(
                issuedCouponId = owned.issuedCouponId,
                couponId = owned.couponId,
                name = owned.name,
                type = owned.type,
                value = owned.value,
                minOrderAmount = owned.minOrderAmount,
                expiredAt = owned.expiredAt,
                status = owned.status,
                usedAt = owned.usedAt,
            )
    }
}
