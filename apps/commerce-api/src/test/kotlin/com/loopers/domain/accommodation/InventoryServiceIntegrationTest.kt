package com.loopers.domain.accommodation

import com.loopers.infrastructure.accommodation.DailyRoomInventoryJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
class InventoryServiceIntegrationTest @Autowired constructor(
    private val inventoryService: InventoryService,
    private val dailyRoomInventoryJpaRepository: DailyRoomInventoryJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val roomTypeId = 100L
    private val date = LocalDate.of(2026, 6, 10)

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("재고를 차감할 때,")
    @Nested
    inner class Reserve {
        @DisplayName("잔여가 있으면 차감되어 remaining 이 줄어든다.")
        @Test
        fun decrementsRemaining_whenAvailable() {
            // arrange
            val seeded = dailyRoomInventoryJpaRepository.save(DailyRoomInventory(roomTypeId, date, remaining = 2))

            // act
            inventoryService.reserve(roomTypeId, listOf(date))

            // assert
            val remaining = dailyRoomInventoryJpaRepository.findById(seeded.id).get().remaining
            assertThat(remaining).isEqualTo(1)
        }

        @DisplayName("잔여가 0이면, OUT_OF_INVENTORY 예외가 발생한다.")
        @Test
        fun throwsOutOfInventory_whenSoldOut() {
            // arrange
            dailyRoomInventoryJpaRepository.save(DailyRoomInventory(roomTypeId, date, remaining = 0))

            // act & assert
            val exception = assertThrows<CoreException> { inventoryService.reserve(roomTypeId, listOf(date)) }
            assertThat(exception.errorType).isEqualTo(ErrorType.OUT_OF_INVENTORY)
        }

        @DisplayName("여러 날짜 중 하나라도 재고가 없으면, 이미 차감한 날짜도 롤백되어 잔여가 보존된다.")
        @Test
        fun rollsBackAllDates_whenAnyDateUnavailable() {
            // arrange — date1 만 재고 1, date2 는 재고 행이 아예 없음
            val date1 = LocalDate.of(2026, 6, 10)
            val date2 = LocalDate.of(2026, 6, 11)
            val inv1 = dailyRoomInventoryJpaRepository.save(DailyRoomInventory(roomTypeId, date1, remaining = 1))

            // act & assert — 두 번째 날짜에서 실패
            val exception = assertThrows<CoreException> { inventoryService.reserve(roomTypeId, listOf(date1, date2)) }
            assertThat(exception.errorType).isEqualTo(ErrorType.OUT_OF_INVENTORY)

            // assert — 첫 번째 날짜 차감이 트랜잭션 롤백으로 되돌려져 잔여 1 유지
            val remaining = dailyRoomInventoryJpaRepository.findById(inv1.id).get().remaining
            assertThat(remaining).isEqualTo(1)
        }
    }

    @DisplayName("같은 날짜의 마지막 1개를 여러 요청이 동시에 차감하면,")
    @Nested
    inner class Concurrency {
        @DisplayName("DB 차원에서 정확히 1건만 성공하고 나머지는 OUT_OF_INVENTORY 가 되며, 더블부킹이 발생하지 않는다.")
        @Test
        fun onlyOneSucceeds_whenRacingForLastRoom() {
            // arrange — 마지막 1개
            val seeded = dailyRoomInventoryJpaRepository.save(DailyRoomInventory(roomTypeId, date, remaining = 1))
            val threadCount = 10
            val executor = Executors.newFixedThreadPool(threadCount)
            val latch = CountDownLatch(threadCount)
            val success = AtomicInteger(0)
            val outOfInventory = AtomicInteger(0)

            // act — 동시에 차감 시도
            repeat(threadCount) {
                executor.submit {
                    try {
                        inventoryService.reserve(roomTypeId, listOf(date))
                        success.incrementAndGet()
                    } catch (e: CoreException) {
                        if (e.errorType == ErrorType.OUT_OF_INVENTORY) outOfInventory.incrementAndGet()
                    } finally {
                        latch.countDown()
                    }
                }
            }
            latch.await(10, TimeUnit.SECONDS)
            executor.shutdown()

            // assert — 정확히 1건 성공, 나머지 재고부족, 잔여 0, 예상치 못한 실패 없음
            val remaining = dailyRoomInventoryJpaRepository.findById(seeded.id).get().remaining
            assertAll(
                { assertThat(success.get()).isEqualTo(1) },
                { assertThat(outOfInventory.get()).isEqualTo(threadCount - 1) },
                { assertThat(success.get() + outOfInventory.get()).isEqualTo(threadCount) },
                { assertThat(remaining).isEqualTo(0) },
            )
        }
    }
}
