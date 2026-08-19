package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.CouponFacade
import com.loopers.interfaces.api.ApiHeaders
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class CouponV1Controller(
    private val couponFacade: CouponFacade,
) : CouponV1ApiSpec {
    @PostMapping("/api/v1/coupons/{couponId}/issue")
    override fun issue(
        @RequestHeader(ApiHeaders.USER_ID) userId: Long,
        @PathVariable couponId: Long,
    ): ApiResponse<CouponV1Dto.IssuedCouponResponse> =
        couponFacade.issue(userId = userId, templateId = couponId)
            .let { CouponV1Dto.IssuedCouponResponse.from(it) }
            .let { ApiResponse.success(it) }

    @GetMapping("/api/v1/users/me/coupons")
    override fun getMyCoupons(
        @RequestHeader(ApiHeaders.USER_ID) userId: Long,
    ): ApiResponse<List<CouponV1Dto.OwnedCouponResponse>> =
        couponFacade.getMyCoupons(userId)
            .map { CouponV1Dto.OwnedCouponResponse.from(it) }
            .let { ApiResponse.success(it) }
}
