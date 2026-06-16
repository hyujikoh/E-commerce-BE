package com.loopers.interfaces.api.coupon

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Coupon Admin V1 API", description = "쿠폰 템플릿 관리 API (admin)")
interface CouponAdminV1ApiSpec {
    @Operation(summary = "쿠폰 템플릿 목록")
    fun list(page: Int, size: Int): ApiResponse<CouponAdminV1Dto.PageResponse<CouponAdminV1Dto.CouponResponse>>

    @Operation(summary = "쿠폰 템플릿 상세")
    fun get(couponId: Long): ApiResponse<CouponAdminV1Dto.CouponResponse>

    @Operation(summary = "쿠폰 템플릿 등록", description = "정액(FIXED)/정률(RATE) 타입을 지정한다.")
    fun create(request: CouponAdminV1Dto.CreateRequest): ApiResponse<CouponAdminV1Dto.CouponResponse>

    @Operation(summary = "쿠폰 템플릿 수정")
    fun update(couponId: Long, request: CouponAdminV1Dto.UpdateRequest): ApiResponse<CouponAdminV1Dto.CouponResponse>

    @Operation(summary = "쿠폰 템플릿 삭제")
    fun delete(couponId: Long): ApiResponse<Any?>

    @Operation(summary = "특정 쿠폰 발급 내역")
    fun listIssues(
        couponId: Long,
        page: Int,
        size: Int,
    ): ApiResponse<CouponAdminV1Dto.PageResponse<CouponV1Dto.IssuedCouponResponse>>
}
