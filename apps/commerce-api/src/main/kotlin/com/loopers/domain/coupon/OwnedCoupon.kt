package com.loopers.domain.coupon

import java.time.ZonedDateTime

/**
 * 사용자 보유 쿠폰 조회용 읽기 모델(발급분 + 템플릿 정보 결합).
 *
 * status 는 표시용 유효 상태다: 저장된 status 가 USED 면 USED, 그 외 템플릿이 만료됐으면 EXPIRED,
 * 아니면 AVAILABLE. (만료를 별도 배치로 갱신하지 않고 조회 시점에 파생한다.)
 */
data class OwnedCoupon(
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
        fun of(issued: IssuedCoupon, coupon: Coupon, now: ZonedDateTime): OwnedCoupon {
            val effectiveStatus = when {
                issued.status == IssuedCouponStatus.USED -> IssuedCouponStatus.USED
                coupon.isExpired(now) -> IssuedCouponStatus.EXPIRED
                else -> IssuedCouponStatus.AVAILABLE
            }
            return OwnedCoupon(
                issuedCouponId = issued.id,
                couponId = coupon.id,
                name = coupon.name,
                type = coupon.type,
                value = coupon.value,
                minOrderAmount = coupon.minOrderAmount,
                expiredAt = coupon.expiredAt,
                status = effectiveStatus,
                usedAt = issued.usedAt,
            )
        }
    }
}
