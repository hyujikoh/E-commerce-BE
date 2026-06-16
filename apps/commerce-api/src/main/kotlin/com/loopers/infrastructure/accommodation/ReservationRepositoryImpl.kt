package com.loopers.infrastructure.accommodation

import com.loopers.domain.accommodation.Reservation
import com.loopers.domain.accommodation.ReservationRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class ReservationRepositoryImpl(
    private val reservationJpaRepository: ReservationJpaRepository,
) : ReservationRepository {
    override fun save(reservation: Reservation): Reservation = reservationJpaRepository.save(reservation)

    override fun find(id: Long): Reservation? = reservationJpaRepository.findByIdOrNull(id)
}
