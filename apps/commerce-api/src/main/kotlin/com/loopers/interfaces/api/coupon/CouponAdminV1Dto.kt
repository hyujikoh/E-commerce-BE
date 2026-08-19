package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.CouponInfo
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.common.PageResult
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class CouponAdminV1Dto {
    @Schema(description = "쿠폰 템플릿 등록 요청")
    data class CreateRequest(
        @field:NotBlank
        @Schema(description = "쿠폰 이름", example = "여름 휴가 시즌 10% 할인")
        val name: String,
        @field:NotNull
        @Schema(description = "할인 방식", example = "RATE")
        val type: CouponType,
        @field:NotNull
        @Schema(description = "정액=할인 금액(원), 정률=비율(%)", example = "10")
        val value: Long,
        @Schema(description = "최소 주문 금액 조건(선택)", example = "100000")
        val minOrderAmount: Long? = null,
        @field:NotNull
        @Schema(description = "만료 시각(YYYY-MM-DDTHH:mm:ss)", example = "2026-12-31T23:59:59")
        val expiredAt: LocalDateTime,
    ) {
        fun expiredAtAsZoned(): ZonedDateTime = expiredAt.atZone(ZoneId.systemDefault())
    }

    @Schema(description = "쿠폰 템플릿 수정 요청")
    data class UpdateRequest(
        @field:NotBlank
        val name: String,
        @field:NotNull
        val type: CouponType,
        @field:NotNull
        val value: Long,
        val minOrderAmount: Long? = null,
        @field:NotNull
        val expiredAt: LocalDateTime,
    ) {
        fun expiredAtAsZoned(): ZonedDateTime = expiredAt.atZone(ZoneId.systemDefault())
    }

    @Schema(description = "쿠폰 템플릿 응답")
    data class CouponResponse(
        val id: Long,
        val name: String,
        val type: CouponType,
        val value: Long,
        val minOrderAmount: Long?,
        val expiredAt: ZonedDateTime,
    ) {
        companion object {
            fun from(info: CouponInfo): CouponResponse =
                CouponResponse(
                    id = info.id,
                    name = info.name,
                    type = info.type,
                    value = info.value,
                    minOrderAmount = info.minOrderAmount,
                    expiredAt = info.expiredAt,
                )
        }
    }

    @Schema(description = "페이지 응답")
    data class PageResponse<T>(
        val content: List<T>,
        val page: Int,
        val size: Int,
        val totalElements: Long,
        val totalPages: Int,
    ) {
        companion object {
            fun <S, T> of(page: PageResult<S>, transform: (S) -> T): PageResponse<T> =
                PageResponse(
                    content = page.content.map(transform),
                    page = page.page,
                    size = page.size,
                    totalElements = page.totalElements,
                    totalPages = page.totalPages,
                )
        }
    }
}
