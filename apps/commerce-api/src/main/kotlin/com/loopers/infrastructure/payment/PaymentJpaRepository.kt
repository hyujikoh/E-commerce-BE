package com.loopers.infrastructure.payment

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentStatus
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.ZonedDateTime

interface PaymentJpaRepository : JpaRepository<Payment, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    fun findWithLockById(@Param("id") id: Long): Payment?

    fun findByReservationId(reservationId: Long): List<Payment>

    /**
     * 결과 미확정 상태로 [threshold] 이전에 마지막 갱신된 결제 스캔(잠금 없음).
     * 반영 단계(applyPgResult)가 행 잠금으로 직렬화하므로 스캔은 잠그지 않는다 —
     * 외부(PG) 조회를 잠금 보유 상태에서 수행하지 않기 위함이다.
     */
    @Query("select p from Payment p where p.status in :statuses and p.updatedAt <= :threshold order by p.updatedAt asc")
    fun findByStatusInAndUpdatedAtBefore(
        @Param("statuses") statuses: Collection<PaymentStatus>,
        @Param("threshold") threshold: ZonedDateTime,
        pageable: Pageable,
    ): List<Payment>
}
