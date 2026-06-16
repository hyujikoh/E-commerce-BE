package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.CouponFacade
import com.loopers.interfaces.api.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api-admin/v1/coupons")
class CouponAdminV1Controller(
    private val couponFacade: CouponFacade,
) : CouponAdminV1ApiSpec {
    @GetMapping
    override fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<CouponAdminV1Dto.PageResponse<CouponAdminV1Dto.CouponResponse>> {
        val result = couponFacade.listTemplates(page, size)
        return ApiResponse.success(CouponAdminV1Dto.PageResponse.of(result, CouponAdminV1Dto.CouponResponse::from))
    }

    @GetMapping("/{couponId}")
    override fun get(
        @PathVariable couponId: Long,
    ): ApiResponse<CouponAdminV1Dto.CouponResponse> =
        couponFacade.getTemplate(couponId)
            .let { CouponAdminV1Dto.CouponResponse.from(it) }
            .let { ApiResponse.success(it) }

    @PostMapping
    override fun create(
        @Valid @RequestBody request: CouponAdminV1Dto.CreateRequest,
    ): ApiResponse<CouponAdminV1Dto.CouponResponse> {
        val info = couponFacade.createTemplate(
            name = request.name,
            type = request.type,
            value = request.value,
            minOrderAmount = request.minOrderAmount,
            expiredAt = request.expiredAtAsZoned(),
        )
        return ApiResponse.success(CouponAdminV1Dto.CouponResponse.from(info))
    }

    @PutMapping("/{couponId}")
    override fun update(
        @PathVariable couponId: Long,
        @Valid @RequestBody request: CouponAdminV1Dto.UpdateRequest,
    ): ApiResponse<CouponAdminV1Dto.CouponResponse> {
        val info = couponFacade.updateTemplate(
            id = couponId,
            name = request.name,
            type = request.type,
            value = request.value,
            minOrderAmount = request.minOrderAmount,
            expiredAt = request.expiredAtAsZoned(),
        )
        return ApiResponse.success(CouponAdminV1Dto.CouponResponse.from(info))
    }

    @DeleteMapping("/{couponId}")
    override fun delete(
        @PathVariable couponId: Long,
    ): ApiResponse<Any?> {
        couponFacade.deleteTemplate(couponId)
        return ApiResponse.success<Any?>(null)
    }

    @GetMapping("/{couponId}/issues")
    override fun listIssues(
        @PathVariable couponId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<CouponAdminV1Dto.PageResponse<CouponV1Dto.IssuedCouponResponse>> {
        val result = couponFacade.listIssues(couponId, page, size)
        return ApiResponse.success(CouponAdminV1Dto.PageResponse.of(result, CouponV1Dto.IssuedCouponResponse::from))
    }
}
