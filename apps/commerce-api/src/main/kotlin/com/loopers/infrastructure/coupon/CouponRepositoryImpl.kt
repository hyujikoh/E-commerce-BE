package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.PageResult
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component

@Component
class CouponRepositoryImpl(
    private val couponJpaRepository: CouponJpaRepository,
) : CouponRepository {
    override fun save(coupon: Coupon): Coupon = couponJpaRepository.save(coupon)

    override fun find(id: Long): Coupon? = couponJpaRepository.findByIdAndDeletedAtIsNull(id)

    override fun findAllByIds(ids: List<Long>): List<Coupon> =
        if (ids.isEmpty()) emptyList() else couponJpaRepository.findAllByIdInAndDeletedAtIsNull(ids)

    override fun findPage(page: Int, size: Int): PageResult<Coupon> =
        couponJpaRepository
            .findAllByDeletedAtIsNull(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")))
            .toPageResult()
}
