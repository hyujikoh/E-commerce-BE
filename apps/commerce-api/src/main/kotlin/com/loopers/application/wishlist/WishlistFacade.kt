package com.loopers.application.wishlist

import com.loopers.domain.wishlist.WishlistService
import org.springframework.stereotype.Component

@Component
class WishlistFacade(
    private val wishlistService: WishlistService,
) {
    /** 찜 추가 후 갱신된 숙소 찜 수를 반환한다. */
    fun add(userId: Long, propertyId: Long): Long = wishlistService.add(userId, propertyId)

    /** 찜 취소 후 갱신된 숙소 찜 수를 반환한다. */
    fun remove(userId: Long, propertyId: Long): Long = wishlistService.remove(userId, propertyId)

    fun getCount(propertyId: Long): Long = wishlistService.getCount(propertyId)
}
