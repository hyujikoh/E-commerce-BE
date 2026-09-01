package com.loopers.domain.payment

import java.time.ZonedDateTime

/** 결제 애그리거트 영속성 포트. 구현은 infrastructure 계층. */
interface PaymentRepository {
    fun save(payment: Payment): Payment

    fun find(id: Long): Payment?

    /** 행 잠금(FOR UPDATE)과 함께 조회한다. 상태 전이의 동시성 가드 — 트랜잭션 안에서만 호출한다. */
    fun findWithLock(id: Long): Payment?

    fun findByReservationId(reservationId: Long): List<Payment>

    /**
     * 결과 미확정(CREATED/REQUESTED) 상태로 [threshold] 이전에 마지막 갱신된 결제를 최대 [limit]건 조회한다.
     * 콜백 유실·타임아웃 건의 상태 동기화 스캔용 — 잠금 없이 조회하고, 반영은 행 잠금으로 직렬화한다.
     */
    fun findResultPending(threshold: ZonedDateTime, limit: Int): List<Payment>
}
