package com.loopers.application.accommodation

import com.loopers.domain.accommodation.Reservation
import com.loopers.domain.accommodation.ReservationStatus
import java.time.LocalDate
import java.time.ZonedDateTime

data class ReservationInfo(
    val id: Long,
    val guestId: Long,
    val roomTypeId: Long,
    val checkIn: LocalDate,
    val checkOut: LocalDate,
    val status: ReservationStatus,
    val totalAmount: Long,
    val currency: String,
    val expiresAt: ZonedDateTime,
    val nightly: List<Nightly>,
) {
    data class Nightly(val date: LocalDate, val amount: Long)

    companion object {
        fun from(reservation: Reservation): ReservationInfo =
            ReservationInfo(
                id = reservation.id,
                guestId = reservation.guestId,
                roomTypeId = reservation.roomTypeId,
                checkIn = reservation.stayPeriod.checkIn,
                checkOut = reservation.stayPeriod.checkOut,
                status = reservation.status,
                totalAmount = reservation.totalAmount.amount,
                currency = reservation.totalAmount.currency,
                expiresAt = reservation.expiresAt,
                nightly = reservation.nightly
                    .sortedBy { it.date }
                    .map { Nightly(it.date, it.amount.amount) },
            )
    }
}
