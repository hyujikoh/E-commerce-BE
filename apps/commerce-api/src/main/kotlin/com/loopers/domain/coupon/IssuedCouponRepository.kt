package com.loopers.domain.coupon

import com.loopers.domain.common.PageResult
import java.time.ZonedDateTime

/** 발급 쿠폰 영속성 포트. 사용 처리는 동시성 안전을 위해 원자적 조건부 UPDATE 로 구현된다. */
interface IssuedCouponRepository {
    fun save(issuedCoupon: IssuedCoupon): IssuedCoupon

    fun find(id: Long): IssuedCoupon?

    fun findByUserId(userId: Long): List<IssuedCoupon>

    fun findPageByCouponId(couponId: Long, page: Int, size: Int): PageResult<IssuedCoupon>

    fun existsByUserIdAndCouponId(userId: Long, couponId: Long): Boolean

    /**
     * status 가 AVAILABLE 인 경우에만 USED 로 전이하고 usedAt 을 기록한다.
     * @return 변경된 행 수(1=사용 성공, 0=이미 사용됨/없음). 동시 사용 시 정확히 1건만 1을 반환한다.
     */
    fun markUsedIfAvailable(id: Long, usedAt: ZonedDateTime): Int
}
