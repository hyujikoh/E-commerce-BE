package com.loopers.domain.wishlist

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 숙소별 찜 수 비정규화 카운터. property_id당 1행.
 *
 * 동시 찜/찜취소에서 Lost Update가 발생하지 않도록 증감은 엔티티 set이 아니라
 * 원자적 쿼리로만 수행한다(PropertyWishlistCountJpaRepository: upsert +1 / 조건부 -1).
 */
@Entity
@Table(
    name = "property_wishlist_count",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_property_wishlist_count_property", columnNames = ["property_id"]),
    ],
)
class PropertyWishlistCount private constructor(
    propertyId: Long,
    count: Long,
) : BaseEntity() {
    @Column(name = "property_id", nullable = false)
    var propertyId: Long = propertyId
        protected set

    @Column(name = "wishlist_count", nullable = false)
    var count: Long = count
        protected set

    companion object {
        fun of(propertyId: Long, count: Long = 0L): PropertyWishlistCount =
            PropertyWishlistCount(propertyId, count)
    }
}
