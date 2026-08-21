package com.loopers.infrastructure.accommodation

import com.loopers.domain.accommodation.Reservation
import com.loopers.domain.accommodation.ReservationRepository
import com.loopers.domain.accommodation.ReservationStatus
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class ReservationRepositoryImpl(
    private val reservationJpaRepository: ReservationJpaRepository,
) : ReservationRepository {
    override fun save(reservation: Reservation): Reservation = reservationJpaRepository.save(reservation)

    override fun find(id: Long): Reservation? = reservationJpaRepository.findByIdOrNull(id)

    override fun findWithLock(id: Long): Reservation? = reservationJpaRepository.findWithLockById(id)

    override fun findExpiredPendingWithLock(now: ZonedDateTime, limit: Int): List<Reservation> =
        reservationJpaRepository.findExpiredPending(ReservationStatus.PENDING, now, PageRequest.of(0, limit))
}
