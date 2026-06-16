package com.loopers.domain.accommodation

/** 예약 애그리거트 영속성 포트. 구현은 infrastructure 계층. */
interface ReservationRepository {
    fun save(reservation: Reservation): Reservation

    fun find(id: Long): Reservation?
}
