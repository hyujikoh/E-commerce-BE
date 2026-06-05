package com.loopers.domain.accommodation

import java.time.LocalDate

/** 일자별 요금 포트. */
interface DailyRoomRateRepository {
    fun findByRoomTypeAndDates(roomTypeId: Long, dates: List<LocalDate>): List<DailyRoomRate>
}
