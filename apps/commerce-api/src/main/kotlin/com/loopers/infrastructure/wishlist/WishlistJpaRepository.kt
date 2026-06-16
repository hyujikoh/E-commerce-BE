package com.loopers.infrastructure.wishlist

import com.loopers.domain.wishlist.Wishlist
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface WishlistJpaRepository : JpaRepository<Wishlist, Long> {
    /**
     * 중복 찜을 DB 차원에서 멱등 처리한다. UNIQUE(user_id, property_id) 충돌 시 예외 대신 무시(IGNORE)되어
     * 트랜잭션을 rollback-only 로 오염시키지 않는다.
     * @return 신규 삽입 1, 이미 존재(무시) 0.
     */
    @Modifying
    @Query(
        value = "INSERT IGNORE INTO wishlist (user_id, property_id, created_at, updated_at) " +
            "VALUES (:userId, :propertyId, NOW(6), NOW(6))",
        nativeQuery = true,
    )
    fun insertIgnore(@Param("userId") userId: Long, @Param("propertyId") propertyId: Long): Int

    /**
     * 원자적 삭제. 동시 취소 요청 중 한쪽만 1행을 삭제하고 나머지는 0행을 삭제한다.
     * @return 삭제된 행 수(0 또는 1).
     */
    @Modifying(clearAutomatically = true)
    @Query("delete from Wishlist w where w.userId = :userId and w.propertyId = :propertyId")
    fun deleteByUserAndProperty(@Param("userId") userId: Long, @Param("propertyId") propertyId: Long): Int

    fun existsByUserIdAndPropertyId(userId: Long, propertyId: Long): Boolean
}
