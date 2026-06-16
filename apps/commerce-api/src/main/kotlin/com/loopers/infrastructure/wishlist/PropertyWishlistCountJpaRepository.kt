package com.loopers.infrastructure.wishlist

import com.loopers.domain.wishlist.PropertyWishlistCount
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PropertyWishlistCountJpaRepository : JpaRepository<PropertyWishlistCount, Long> {
    /**
     * 숙소 찜 수 원자적 증가. 행이 없으면 1로 생성하고, 있으면 +1 한다(MySQL upsert).
     * 단일 쿼리이므로 동시 증가에서 Lost Update 가 발생하지 않는다.
     */
    @Modifying
    @Query(
        value = "INSERT INTO property_wishlist_count (property_id, wishlist_count, created_at, updated_at) " +
            "VALUES (:propertyId, 1, NOW(6), NOW(6)) " +
            "ON DUPLICATE KEY UPDATE wishlist_count = wishlist_count + 1, updated_at = NOW(6)",
        nativeQuery = true,
    )
    fun upsertIncrement(@Param("propertyId") propertyId: Long): Int

    /** 숙소 찜 수 원자적 감소. count>0 인 경우에만 -1 하여 음수로 내려가지 않는다. */
    @Modifying
    @Query(
        value = "UPDATE property_wishlist_count SET wishlist_count = wishlist_count - 1, updated_at = NOW(6) " +
            "WHERE property_id = :propertyId AND wishlist_count > 0",
        nativeQuery = true,
    )
    fun decrement(@Param("propertyId") propertyId: Long): Int

    fun findByPropertyId(propertyId: Long): PropertyWishlistCount?
}
