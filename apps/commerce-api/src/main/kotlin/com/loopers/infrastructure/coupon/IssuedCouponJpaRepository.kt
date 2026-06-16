package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.IssuedCoupon
import com.loopers.domain.coupon.IssuedCouponStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.ZonedDateTime

interface IssuedCouponJpaRepository : JpaRepository<IssuedCoupon, Long> {
    fun findAllByUserId(userId: Long): List<IssuedCoupon>

    fun findAllByCouponId(couponId: Long, pageable: Pageable): Page<IssuedCoupon>

    fun existsByUserIdAndCouponId(userId: Long, couponId: Long): Boolean

    /**
     * 원자적 사용 전이 — status 가 AVAILABLE 인 행만 USED 로 변경한다.
     * 동시 요청 중 한쪽만 1행을 변경하고 나머지는 0행을 변경한다(쿠폰 단일 사용 보장).
     * @return 변경된 행 수(0 또는 1).
     */
    @Modifying(clearAutomatically = true)
    @Query(
        "update IssuedCoupon ic set ic.status = :used, ic.usedAt = :usedAt " +
            "where ic.id = :id and ic.status = :available",
    )
    fun markUsedIfAvailable(
        @Param("id") id: Long,
        @Param("used") used: IssuedCouponStatus,
        @Param("available") available: IssuedCouponStatus,
        @Param("usedAt") usedAt: ZonedDateTime,
    ): Int
}
