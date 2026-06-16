package com.loopers.domain.wishlist

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 찜 추가/취소와 숙소별 찜 수 카운터를 한 트랜잭션으로 묶는다.
 *
 * 동시성 전략:
 * - 찜 추가: UNIQUE(user_id, property_id) + INSERT IGNORE로 중복 찜을 멱등 처리.
 *   실제로 추가된 경우에만 카운터를 원자적 upsert(+1).
 * - 찜 취소: 벌크 DELETE의 영향 행 수로 실제 삭제 여부를 판단.
 *   실제로 삭제된 경우에만 카운터를 조건부(-1, count>0) 감소.
 * 카운터 증감은 DB 단일 원자 쿼리이므로 read-modify-write Lost Update가 발생하지 않는다.
 */
@Component
class WishlistService(
    private val wishlistRepository: WishlistRepository,
    private val countRepository: PropertyWishlistCountRepository,
) {
    /** 찜 추가 후, 같은 트랜잭션에서 갱신된 카운트를 읽어 반환한다(read-your-writes 일관성). */
    @Transactional
    fun add(userId: Long, propertyId: Long): Long {
        if (wishlistRepository.addIfAbsent(userId, propertyId)) {
            countRepository.increment(propertyId)
        }
        return countRepository.getCount(propertyId)
    }

    /** 찜 취소 후, 같은 트랜잭션에서 갱신된 카운트를 읽어 반환한다(read-your-writes 일관성). */
    @Transactional
    fun remove(userId: Long, propertyId: Long): Long {
        if (wishlistRepository.remove(userId, propertyId)) {
            countRepository.decrement(propertyId)
        }
        return countRepository.getCount(propertyId)
    }

    @Transactional(readOnly = true)
    fun getCount(propertyId: Long): Long = countRepository.getCount(propertyId)

    @Transactional(readOnly = true)
    fun isWishlisted(userId: Long, propertyId: Long): Boolean =
        wishlistRepository.exists(userId, propertyId)
}
