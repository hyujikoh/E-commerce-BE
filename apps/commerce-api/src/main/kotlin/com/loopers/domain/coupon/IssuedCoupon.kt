package com.loopers.domain.coupon

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.ZonedDateTime

/**
 * 사용자에게 발급된 쿠폰 1장. (user_id, coupon_id) 1건만 존재한다(중복 발급 방지 + 동시 발급 backstop).
 *
 * 사용(AVAILABLE → USED) 전이는 엔티티 메서드가 아니라 원자적 조건부 UPDATE
 * (IssuedCouponJpaRepository.markUsedIfAvailable)로 수행한다. 동일 쿠폰 동시 사용 시 DB 차원에서
 * 정확히 1건만 USED 로 바뀌도록 보장하기 위함이다(상태를 읽고-쓰는 경합을 SQL 한 줄에 응집).
 */
@Entity
@Table(
    name = "issued_coupon",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_issued_coupon_user_coupon", columnNames = ["user_id", "coupon_id"]),
    ],
)
class IssuedCoupon private constructor(
    userId: Long,
    couponId: Long,
) : BaseEntity() {
    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        protected set

    @Column(name = "coupon_id", nullable = false)
    var couponId: Long = couponId
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: IssuedCouponStatus = IssuedCouponStatus.AVAILABLE
        protected set

    @Column(name = "used_at")
    var usedAt: ZonedDateTime? = null
        protected set

    companion object {
        fun issue(userId: Long, couponId: Long): IssuedCoupon = IssuedCoupon(userId, couponId)
    }
}
