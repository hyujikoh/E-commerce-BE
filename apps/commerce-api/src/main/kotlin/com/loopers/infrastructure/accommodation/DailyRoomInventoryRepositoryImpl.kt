package com.loopers.infrastructure.accommodation

import com.loopers.domain.accommodation.DailyRoomInventoryRepository
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class DailyRoomInventoryRepositoryImpl(
    private val dailyRoomInventoryJpaRepository: DailyRoomInventoryJpaRepository,
) : DailyRoomInventoryRepository {
    override fun decrementIfAvailable(roomTypeId: Long, date: LocalDate): Boolean =
        dailyRoomInventoryJpaRepository.decrementIfAvailable(roomTypeId, date) == 1
}
