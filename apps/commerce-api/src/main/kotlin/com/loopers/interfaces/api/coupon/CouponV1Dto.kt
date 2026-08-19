package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.IssuedCouponInfo
import com.loopers.application.coupon.OwnedCouponInfo
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.IssuedCouponStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.ZonedDateTime

class CouponV1Dto {
    @Schema(description = "발급 쿠폰 응답")
    data class IssuedCouponResponse(
        val issuedCouponId: Long,
        val couponId: Long,
        val status: IssuedCouponStatus,
        val usedAt: ZonedDateTime?,
        val issuedAt: ZonedDateTime,
    ) {
        companion object {
            fun from(info: IssuedCouponInfo): IssuedCouponResponse =
                IssuedCouponResponse(
                    issuedCouponId = info.id,
                    couponId = info.couponId,
                    status = info.status,
                    usedAt = info.usedAt,
                    issuedAt = info.issuedAt,
                )
        }
    }

    @Schema(description = "내 보유 쿠폰 응답")
    data class OwnedCouponResponse(
        val issuedCouponId: Long,
        val couponId: Long,
        val name: String,
        val type: CouponType,
        val value: Long,
        val minOrderAmount: Long?,
        val expiredAt: ZonedDateTime,
        @Schema(description = "표시용 유효 상태: AVAILABLE | USED | EXPIRED")
        val status: IssuedCouponStatus,
        val usedAt: ZonedDateTime?,
    ) {
        companion object {
            fun from(info: OwnedCouponInfo): OwnedCouponResponse =
                OwnedCouponResponse(
                    issuedCouponId = info.issuedCouponId,
                    couponId = info.couponId,
                    name = info.name,
                    type = info.type,
                    value = info.value,
                    minOrderAmount = info.minOrderAmount,
                    expiredAt = info.expiredAt,
                    status = info.status,
                    usedAt = info.usedAt,
                )
        }
    }
}
