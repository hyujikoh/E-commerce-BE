package com.loopers.domain.coupon

/**
 * 발급된 쿠폰의 상태.
 * - AVAILABLE: 사용 가능
 * - USED: 사용 완료(재사용 불가)
 * - EXPIRED: 만료(표시용 — 저장 상태는 AVAILABLE 이지만 템플릿 만료 시 조회 시점에 EXPIRED 로 노출)
 */
enum class IssuedCouponStatus {
    AVAILABLE,
    USED,
    EXPIRED,
}
