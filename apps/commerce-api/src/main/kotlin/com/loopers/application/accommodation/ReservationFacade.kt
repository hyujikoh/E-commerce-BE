package com.loopers.application.accommodation

import com.loopers.domain.accommodation.ReservationService
import com.loopers.domain.accommodation.vo.DateRange
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class ReservationFacade(
    private val reservationService: ReservationService,
) {
    fun create(guestId: Long, roomTypeId: Long, checkIn: LocalDate, checkOut: LocalDate): ReservationInfo {
        val reservation = reservationService.create(
            guestId = guestId,
            roomTypeId = roomTypeId,
            stayPeriod = DateRange(checkIn, checkOut),
        )
        return ReservationInfo.from(reservation)
    }
}
