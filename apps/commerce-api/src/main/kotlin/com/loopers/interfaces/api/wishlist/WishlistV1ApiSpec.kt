package com.loopers.interfaces.api.wishlist

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Wishlist V1 API", description = "숙소 찜 API")
interface WishlistV1ApiSpec {
    @Operation(summary = "찜 추가", description = "숙소를 찜한다. 이미 찜한 경우 멱등 처리된다.")
    @Parameter(name = "X-USER-ID", `in` = ParameterIn.HEADER, required = true, description = "사용자 ID")
    fun add(userId: Long, propertyId: Long): ApiResponse<WishlistV1Dto.WishlistResponse>

    @Operation(summary = "찜 취소", description = "숙소 찜을 취소한다. 찜하지 않은 경우 멱등 처리된다.")
    @Parameter(name = "X-USER-ID", `in` = ParameterIn.HEADER, required = true, description = "사용자 ID")
    fun remove(userId: Long, propertyId: Long): ApiResponse<WishlistV1Dto.WishlistResponse>

    @Operation(summary = "숙소 찜 수 조회")
    fun getCount(propertyId: Long): ApiResponse<WishlistV1Dto.WishlistResponse>
}
