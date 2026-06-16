package com.loopers.domain.accommodation

import com.loopers.domain.accommodation.vo.DateRange
import com.loopers.domain.accommodation.vo.Money
import com.loopers.infrastructure.accommodation.DailyRoomInventoryJpaRepository
import com.loopers.infrastructure.accommodation.DailyRoomRateJpaRepository
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

@SpringBootTest
class ReservationServiceIntegrationTest @Autowired constructor(
    private val reservationService: ReservationService,
    private val dailyRoomRateJpaRepository: DailyRoomRateJpaRepository,
    private val dailyRoomInventoryJpaRepository: DailyRoomInventoryJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val guestId = 1L
    private val roomTypeId = 100L
    private val checkIn = LocalDate.of(2026, 6, 10)
    private val checkOut = LocalDate.of(2026, 6, 12) // 2박
    private val dates = listOf(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 11))

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun seedRates(amount: Long = 50_000) =
        dates.map { dailyRoomRateJpaRepository.save(DailyRoomRate(roomTypeId, it, Money.krw(amount))) }

    private fun seedInventory(remaining: Int = 1) =
        dates.map { dailyRoomInventoryJpaRepository.save(DailyRoomInventory(roomTypeId, it, remaining)) }

    @DisplayName("예약을 생성할 때,")
    @Nested
    inner class Create {
        @DisplayName("요금·재고가 충분하면, PENDING 예약을 만들고 총액 스냅샷을 적재하며 일자별 재고를 차감한다.")
        @Test
        fun createsPendingReservation_andDecrementsInventory() {
            // arrange
            seedRates(amount = 50_000)
            val inventories = seedInventory(remaining = 1)

            // act
            val reservation = reservationService.create(guestId, roomTypeId, DateRange(checkIn, checkOut))

            // assert
            val remaining = inventories.map { dailyRoomInventoryJpaRepository.findById(it.id).get().remaining }
            assertAll(
                { assertThat(reservation.id).isPositive() },
                { assertThat(reservation.status).isEqualTo(ReservationStatus.PENDING) },
                { assertThat(reservation.totalAmount).isEqualTo(Money.krw(100_000)) },
                { assertThat(reservation.nightly).hasSize(2) },
                { assertThat(remaining).containsExactly(0, 0) },
            )
        }

        @DisplayName("일부 일자에 요금이 없으면, RATE_NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsRateNotFound_whenRateMissing() {
            // arrange — 재고만 있고 요금 없음
            seedInventory(remaining = 1)

            // act & assert
            val exception = assertThrows<CoreException> {
                reservationService.create(guestId, roomTypeId, DateRange(checkIn, checkOut))
            }
            assertThat(exception.errorType).isEqualTo(ErrorType.RATE_NOT_FOUND)
        }

        @DisplayName("재고가 없으면, OUT_OF_INVENTORY 예외가 발생한다.")
        @Test
        fun throwsOutOfInventory_whenNoInventory() {
            // arrange — 요금만 있고 재고 없음
            seedRates()

            // act & assert
            val exception = assertThrows<CoreException> {
                reservationService.create(guestId, roomTypeId, DateRange(checkIn, checkOut))
            }
            assertThat(exception.errorType).isEqualTo(ErrorType.OUT_OF_INVENTORY)
        }
    }
}
