package com.loopers.infrastructure.accommodation

import com.loopers.domain.accommodation.DailyRoomRate
import com.loopers.domain.accommodation.DailyRoomRateRepository
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class DailyRoomRateRepositoryImpl(
    private val dailyRoomRateJpaRepository: DailyRoomRateJpaRepository,
) : DailyRoomRateRepository {
    override fun findByRoomTypeAndDates(roomTypeId: Long, dates: List<LocalDate>): List<DailyRoomRate> =
        dailyRoomRateJpaRepository.findAllByRoomTypeIdAndDateIn(roomTypeId, dates)
}
