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

    /**
     * 원자적 복구 전이 — status 가 USED 인 행만 AVAILABLE 로 되돌리고 usedAt 을 초기화한다.
     * 예약 취소/만료 시 쿠폰을 재사용 가능 상태로 복구하는 데 쓰인다.
     * clearAutomatically 를 쓰지 않는다 — 취소/만료 트랜잭션의 Reservation 상태 전이(dirty)가
     * clear 로 유실되는 것을 막는다. 이 트랜잭션에서 쿠폰 엔티티를 다시 읽는 경로는 없다.
     * @return 변경된 행 수(0 또는 1).
     */
    @Modifying
    @Query(
        "update IssuedCoupon ic set ic.status = :available, ic.usedAt = null " +
            "where ic.id = :id and ic.status = :used",
    )
    fun restoreIfUsed(
        @Param("id") id: Long,
        @Param("used") used: IssuedCouponStatus,
        @Param("available") available: IssuedCouponStatus,
    ): Int
}
