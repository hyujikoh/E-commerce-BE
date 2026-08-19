package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.Coupon
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface CouponJpaRepository : JpaRepository<Coupon, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): Coupon?

    fun findAllByIdInAndDeletedAtIsNull(ids: List<Long>): List<Coupon>

    fun findAllByDeletedAtIsNull(pageable: Pageable): Page<Coupon>
}
