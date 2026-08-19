package com.loopers.domain.wishlist

import com.loopers.infrastructure.wishlist.WishlistJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
class WishlistServiceIntegrationTest @Autowired constructor(
    private val wishlistService: WishlistService,
    private val wishlistJpaRepository: WishlistJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val userId = 1L
    private val propertyId = 1024L

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("찜을 추가/취소할 때,")
    @Nested
    inner class AddRemove {
        @DisplayName("찜하면 찜 수가 1 증가하고 찜 상태가 된다.")
        @Test
        fun addsWishlist() {
            wishlistService.add(userId, propertyId)

            assertAll(
                { assertThat(wishlistService.getCount(propertyId)).isEqualTo(1L) },
                { assertThat(wishlistService.isWishlisted(userId, propertyId)).isTrue() },
            )
        }

        @DisplayName("같은 사용자가 같은 숙소를 다시 찜해도(멱등), 찜 수는 1로 유지된다.")
        @Test
        fun idempotentDuplicateAdd() {
            wishlistService.add(userId, propertyId)
            wishlistService.add(userId, propertyId)
            wishlistService.add(userId, propertyId)

            assertThat(wishlistService.getCount(propertyId)).isEqualTo(1L)
        }

        @DisplayName("찜을 취소하면 찜 수가 1 감소한다.")
        @Test
        fun removesWishlist() {
            wishlistService.add(userId, propertyId)

            wishlistService.remove(userId, propertyId)

            assertAll(
                { assertThat(wishlistService.getCount(propertyId)).isEqualTo(0L) },
                { assertThat(wishlistService.isWishlisted(userId, propertyId)).isFalse() },
            )
        }

        @DisplayName("찜하지 않은 상태에서 취소해도(멱등), 찜 수는 0으로 유지되고 음수가 되지 않는다.")
        @Test
        fun idempotentRemoveWhenAbsent() {
            wishlistService.remove(userId, propertyId)

            assertThat(wishlistService.getCount(propertyId)).isEqualTo(0L)
        }

        @DisplayName("찜이 한 번도 없던 숙소의 찜 수는 0이다.")
        @Test
        fun zeroCountWhenNeverWishlisted() {
            assertThat(wishlistService.getCount(propertyId)).isEqualTo(0L)
        }
    }

    @DisplayName("동일 숙소에 대해 여러 사용자가 동시에 찜하면,")
    @Nested
    inner class ConcurrentAdd {
        @DisplayName("Lost Update 없이 찜 수가 사용자 수만큼 정확히 반영된다.")
        @Test
        fun countReflectsAllUsers() {
            val userCount = 30
            val executor = Executors.newFixedThreadPool(16)
            val latch = CountDownLatch(userCount)

            repeat(userCount) { i ->
                executor.submit {
                    try {
                        wishlistService.add(i.toLong(), propertyId)
                    } finally {
                        latch.countDown()
                    }
                }
            }
            latch.await(10, TimeUnit.SECONDS)
            executor.shutdown()

            assertThat(wishlistService.getCount(propertyId)).isEqualTo(userCount.toLong())
        }
    }

    @DisplayName("동일 사용자가 같은 숙소를 동시에 여러 번 찜해도,")
    @Nested
    inner class ConcurrentDuplicateAdd {
        @DisplayName("찜 수는 정확히 1이다(중복 멱등).")
        @Test
        fun countStaysOne() {
            val threadCount = 20
            val executor = Executors.newFixedThreadPool(16)
            val latch = CountDownLatch(threadCount)

            repeat(threadCount) {
                executor.submit {
                    try {
                        wishlistService.add(userId, propertyId)
                    } catch (e: Exception) {
                        // 동시 삽입 경합은 멱등 처리/UNIQUE 로 흡수 — 무시
                    } finally {
                        latch.countDown()
                    }
                }
            }
            latch.await(10, TimeUnit.SECONDS)
            executor.shutdown()

            assertThat(wishlistService.getCount(propertyId)).isEqualTo(1L)
        }
    }

    @DisplayName("여러 사용자가 찜한 뒤 동시에 모두 취소하면,")
    @Nested
    inner class ConcurrentRemove {
        @DisplayName("찜 수가 0으로 정확히 수렴한다.")
        @Test
        fun countConvergesToZero() {
            val userCount = 30
            repeat(userCount) { i -> wishlistService.add(i.toLong(), propertyId) }
            assertThat(wishlistService.getCount(propertyId)).isEqualTo(userCount.toLong())

            val executor = Executors.newFixedThreadPool(16)
            val latch = CountDownLatch(userCount)
            repeat(userCount) { i ->
                executor.submit {
                    try {
                        wishlistService.remove(i.toLong(), propertyId)
                    } finally {
                        latch.countDown()
                    }
                }
            }
            latch.await(10, TimeUnit.SECONDS)
            executor.shutdown()

            assertThat(wishlistService.getCount(propertyId)).isEqualTo(0L)
        }
    }
}
