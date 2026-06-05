package com.loopers.infrastructure.accommodation

import com.loopers.domain.accommodation.DailyRoomRate
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface DailyRoomRateJpaRepository : JpaRepository<DailyRoomRate, Long> {
    fun findAllByRoomTypeIdAndDateIn(roomTypeId: Long, dates: Collection<LocalDate>): List<DailyRoomRate>
}
