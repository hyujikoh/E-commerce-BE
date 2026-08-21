package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponService
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.common.PageResult
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class CouponFacade(
    private val couponService: CouponService,
) {
    // ── user ──

    fun issue(userId: Long, templateId: Long): IssuedCouponInfo =
        IssuedCouponInfo.from(couponService.issue(userId, templateId))

    fun getMyCoupons(userId: Long): List<OwnedCouponInfo> =
        couponService.getMyCoupons(userId).map(OwnedCouponInfo::from)

    // ── admin ──

    fun createTemplate(
        name: String,
        type: CouponType,
        value: Long,
        minOrderAmount: Long?,
        expiredAt: ZonedDateTime,
    ): CouponInfo = CouponInfo.from(couponService.createTemplate(name, type, value, minOrderAmount, expiredAt))

    fun getTemplate(id: Long): CouponInfo = CouponInfo.from(couponService.getTemplate(id))

    fun listTemplates(page: Int, size: Int): PageResult<CouponInfo> =
        couponService.listTemplates(page, size).map(CouponInfo::from)

    fun updateTemplate(
        id: Long,
        name: String,
        type: CouponType,
        value: Long,
        minOrderAmount: Long?,
        expiredAt: ZonedDateTime,
    ): CouponInfo = CouponInfo.from(couponService.updateTemplate(id, name, type, value, minOrderAmount, expiredAt))

    fun deleteTemplate(id: Long) = couponService.deleteTemplate(id)

    fun listIssues(couponId: Long, page: Int, size: Int): PageResult<IssuedCouponInfo> =
        couponService.listIssues(couponId, page, size).map(IssuedCouponInfo::from)
}
