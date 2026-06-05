package com.loopers.domain.accommodation

/**
 * 예약 상태 머신.
 * PENDING → CONFIRMED → CHECKED_IN → CHECKED_OUT
 * PENDING/CONFIRMED → CANCELLED, CONFIRMED → NO_SHOW
 */
enum class ReservationStatus {
    PENDING,
    CONFIRMED,
    CHECKED_IN,
    CHECKED_OUT,
    CANCELLED,
    NO_SHOW,
}
