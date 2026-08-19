package com.loopers.interfaces.api.coupon

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Coupon V1 API", description = "대고객 쿠폰 API")
interface CouponV1ApiSpec {
    @Operation(summary = "쿠폰 발급", description = "쿠폰 템플릿을 사용자에게 발급한다. 사용자당 동일 템플릿 1회만 발급된다.")
    @Parameter(name = "X-USER-ID", `in` = ParameterIn.HEADER, required = true, description = "사용자 ID")
    fun issue(userId: Long, couponId: Long): ApiResponse<CouponV1Dto.IssuedCouponResponse>

    @Operation(summary = "내 쿠폰 목록", description = "보유 쿠폰을 표시용 상태(AVAILABLE/USED/EXPIRED)와 함께 조회한다.")
    @Parameter(name = "X-USER-ID", `in` = ParameterIn.HEADER, required = true, description = "사용자 ID")
    fun getMyCoupons(userId: Long): ApiResponse<List<CouponV1Dto.OwnedCouponResponse>>
}
