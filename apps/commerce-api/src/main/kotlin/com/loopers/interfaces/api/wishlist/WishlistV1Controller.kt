package com.loopers.interfaces.api.wishlist

import com.loopers.application.wishlist.WishlistFacade
import com.loopers.interfaces.api.ApiHeaders
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/properties/{propertyId}/wishlist")
class WishlistV1Controller(
    private val wishlistFacade: WishlistFacade,
) : WishlistV1ApiSpec {
    @PostMapping
    override fun add(
        @RequestHeader(ApiHeaders.USER_ID) userId: Long,
        @PathVariable propertyId: Long,
    ): ApiResponse<WishlistV1Dto.WishlistResponse> {
        val count = wishlistFacade.add(userId, propertyId)
        return ApiResponse.success(WishlistV1Dto.WishlistResponse(propertyId, count))
    }

    @DeleteMapping
    override fun remove(
        @RequestHeader(ApiHeaders.USER_ID) userId: Long,
        @PathVariable propertyId: Long,
    ): ApiResponse<WishlistV1Dto.WishlistResponse> {
        val count = wishlistFacade.remove(userId, propertyId)
        return ApiResponse.success(WishlistV1Dto.WishlistResponse(propertyId, count))
    }

    @GetMapping("/count")
    override fun getCount(
        @PathVariable propertyId: Long,
    ): ApiResponse<WishlistV1Dto.WishlistResponse> {
        val count = wishlistFacade.getCount(propertyId)
        return ApiResponse.success(WishlistV1Dto.WishlistResponse(propertyId, count))
    }
}
