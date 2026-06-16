package com.loopers.infrastructure.wishlist

import com.loopers.domain.wishlist.PropertyWishlistCountRepository
import org.springframework.stereotype.Component

@Component
class PropertyWishlistCountRepositoryImpl(
    private val countJpaRepository: PropertyWishlistCountJpaRepository,
) : PropertyWishlistCountRepository {
    override fun increment(propertyId: Long) {
        countJpaRepository.upsertIncrement(propertyId)
    }

    override fun decrement(propertyId: Long) {
        countJpaRepository.decrement(propertyId)
    }

    override fun getCount(propertyId: Long): Long =
        countJpaRepository.findByPropertyId(propertyId)?.count ?: 0L
}
