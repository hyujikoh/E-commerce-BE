package com.loopers.application.coupon

import com.loopers.domain.coupon.IssuedCoupon
import com.loopers.domain.coupon.IssuedCouponStatus
import java.time.ZonedDateTime

/** 발급 쿠폰 정보(발급 응답 · admin 발급내역용). 저장된 상태를 그대로 노출한다. */
data class IssuedCouponInfo(
    val id: Long,
    val userId: Long,
    val couponId: Long,
    val status: IssuedCouponStatus,
    val usedAt: ZonedDateTime?,
    val issuedAt: ZonedDateTime,
) {
    companion object {
        fun from(issued: IssuedCoupon): IssuedCouponInfo =
            IssuedCouponInfo(
                id = issued.id,
                userId = issued.userId,
                couponId = issued.couponId,
                status = issued.status,
                usedAt = issued.usedAt,
                issuedAt = issued.createdAt,
            )
    }
}
