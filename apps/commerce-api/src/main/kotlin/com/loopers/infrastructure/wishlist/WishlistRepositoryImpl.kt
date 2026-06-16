package com.loopers.infrastructure.wishlist

import com.loopers.domain.wishlist.WishlistRepository
import org.springframework.stereotype.Component

@Component
class WishlistRepositoryImpl(
    private val wishlistJpaRepository: WishlistJpaRepository,
) : WishlistRepository {
    override fun addIfAbsent(userId: Long, propertyId: Long): Boolean =
        wishlistJpaRepository.insertIgnore(userId, propertyId) > 0

    override fun remove(userId: Long, propertyId: Long): Boolean =
        wishlistJpaRepository.deleteByUserAndProperty(userId, propertyId) > 0

    override fun exists(userId: Long, propertyId: Long): Boolean =
        wishlistJpaRepository.existsByUserIdAndPropertyId(userId, propertyId)
}
