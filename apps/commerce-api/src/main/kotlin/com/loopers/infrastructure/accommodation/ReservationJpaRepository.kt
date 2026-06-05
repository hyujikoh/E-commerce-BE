package com.loopers.infrastructure.accommodation

import com.loopers.domain.accommodation.Reservation
import org.springframework.data.jpa.repository.JpaRepository

interface ReservationJpaRepository : JpaRepository<Reservation, Long>
