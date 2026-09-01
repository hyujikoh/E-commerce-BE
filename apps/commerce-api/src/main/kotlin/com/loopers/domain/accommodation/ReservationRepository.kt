package com.loopers.domain.accommodation

import java.time.ZonedDateTime

/** 예약 애그리거트 영속성 포트. 구현은 infrastructure 계층. */
interface ReservationRepository {
    fun save(reservation: Reservation): Reservation

    fun find(id: Long): Reservation?

    /** 행 잠금(FOR UPDATE)과 함께 조회한다. 상태 전이의 동시성 가드 — 트랜잭션 안에서만 호출한다. */
    fun findWithLock(id: Long): Reservation?

    /**
     * 만료 시각이 지난 PENDING 예약을 행 잠금과 함께 최대 [limit]건 조회한다.
     * FOR UPDATE SKIP LOCKED — 다른 인스턴스가 잠근 행은 건너뛰어 스케줄러 다중 인스턴스에 안전하다.
     */
    fun findExpiredPendingWithLock(now: ZonedDateTime, limit: Int): List<Reservation>
}
