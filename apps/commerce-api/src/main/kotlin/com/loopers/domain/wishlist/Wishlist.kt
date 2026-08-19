package com.loopers.domain.wishlist

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 사용자의 숙소 찜 1건. (user_id, property_id) 1건만 존재한다(중복 찜 방지).
 *
 * 추가/삭제는 동시성 안전을 위해 엔티티 상태 변경이 아니라 원자적 쿼리로 수행한다
 * (WishlistJpaRepository: INSERT IGNORE / 벌크 DELETE). 멱등성(중복 찜·중복 취소)을 DB 차원에 응집한다.
 */
@Entity
@Table(
    name = "wishlist",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_wishlist_user_property", columnNames = ["user_id", "property_id"]),
    ],
)
class Wishlist private constructor(
    userId: Long,
    propertyId: Long,
) : BaseEntity() {
    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        protected set

    @Column(name = "property_id", nullable = false)
    var propertyId: Long = propertyId
        protected set

    companion object {
        fun of(userId: Long, propertyId: Long): Wishlist = Wishlist(userId, propertyId)
    }
}
