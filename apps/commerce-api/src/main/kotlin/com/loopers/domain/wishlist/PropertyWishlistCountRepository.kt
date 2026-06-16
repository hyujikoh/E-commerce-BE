package com.loopers.domain.wishlist

interface PropertyWishlistCountRepository {
    /** 숙소 찜 수를 원자적으로 +1 한다. 행이 없으면 1로 생성한다(upsert). */
    fun increment(propertyId: Long)

    /** 숙소 찜 수를 원자적으로 -1 한다. 0 이하로는 내려가지 않는다. */
    fun decrement(propertyId: Long)

    /** 현재 찜 수. 행이 없으면 0. */
    fun getCount(propertyId: Long): Long
}
