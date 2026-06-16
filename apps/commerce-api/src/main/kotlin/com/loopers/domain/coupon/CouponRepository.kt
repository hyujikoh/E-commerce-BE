package com.loopers.domain.coupon

/** 쿠폰 템플릿 영속성 포트. 조회는 소프트 삭제된 템플릿을 제외한다. */
interface CouponRepository {
    fun save(coupon: Coupon): Coupon

    /** 소프트 삭제되지 않은 템플릿 단건. */
    fun find(id: Long): Coupon?

    fun findAllByIds(ids: List<Long>): List<Coupon>

    fun findPage(page: Int, size: Int): PageResult<Coupon>
}
