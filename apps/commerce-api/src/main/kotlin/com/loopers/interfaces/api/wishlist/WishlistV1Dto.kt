package com.loopers.interfaces.api.wishlist

import io.swagger.v3.oas.annotations.media.Schema

class WishlistV1Dto {
    @Schema(description = "찜 결과 응답")
    data class WishlistResponse(
        @Schema(description = "숙소 ID")
        val propertyId: Long,
        @Schema(description = "현재 숙소 찜 수")
        val wishlistCount: Long,
    )
}
