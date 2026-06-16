package com.loopers.domain.wishlist

interface WishlistRepository {
    /**
     * (userId, propertyId) 찜을 추가한다. 이미 존재하면 무시한다(멱등).
     * @return 신규로 추가되었으면 true, 이미 존재해 무시되었으면 false
     */
    fun addIfAbsent(userId: Long, propertyId: Long): Boolean

    /**
     * (userId, propertyId) 찜을 제거한다.
     * @return 실제로 삭제된 행이 있으면 true, 없으면 false
     */
    fun remove(userId: Long, propertyId: Long): Boolean

    fun exists(userId: Long, propertyId: Long): Boolean
}
