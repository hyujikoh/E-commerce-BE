package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.IssuedCoupon
import com.loopers.domain.coupon.IssuedCouponRepository
import com.loopers.domain.coupon.IssuedCouponStatus
import com.loopers.domain.coupon.PageResult
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class IssuedCouponRepositoryImpl(
    private val issuedCouponJpaRepository: IssuedCouponJpaRepository,
) : IssuedCouponRepository {
    override fun save(issuedCoupon: IssuedCoupon): IssuedCoupon = issuedCouponJpaRepository.save(issuedCoupon)

    override fun find(id: Long): IssuedCoupon? = issuedCouponJpaRepository.findByIdOrNull(id)

    override fun findByUserId(userId: Long): List<IssuedCoupon> = issuedCouponJpaRepository.findAllByUserId(userId)

    override fun findPageByCouponId(couponId: Long, page: Int, size: Int): PageResult<IssuedCoupon> =
        issuedCouponJpaRepository
            .findAllByCouponId(couponId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")))
            .toPageResult()

    override fun existsByUserIdAndCouponId(userId: Long, couponId: Long): Boolean =
        issuedCouponJpaRepository.existsByUserIdAndCouponId(userId, couponId)

    override fun markUsedIfAvailable(id: Long, usedAt: ZonedDateTime): Int =
        issuedCouponJpaRepository.markUsedIfAvailable(
            id = id,
            used = IssuedCouponStatus.USED,
            available = IssuedCouponStatus.AVAILABLE,
            usedAt = usedAt,
        )
}
