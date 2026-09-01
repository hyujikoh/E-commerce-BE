package com.loopers.infrastructure.accommodation

import com.loopers.domain.accommodation.Reservation
import com.loopers.domain.accommodation.ReservationStatus
import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints
import org.springframework.data.repository.query.Param
import java.time.ZonedDateTime

interface ReservationJpaRepository : JpaRepository<Reservation, Long> {
    /**
     * nightly 스냅샷을 fetch join 으로 함께 적재한다 — Facade 가 트랜잭션 밖에서
     * ReservationInfo 로 변환하므로 lazy 컬렉션이 초기화된 채 반환되어야 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r left join fetch r.nightlyItems where r.id = :id")
    fun findWithLockById(@Param("id") id: Long): Reservation?

    /**
     * 만료 시각이 지난 PENDING 예약을 잠금과 함께 스캔한다.
     * lock timeout -2 는 Hibernate 의 SKIP_LOCKED — 타 인스턴스가 잠근 행은 대기 없이 건너뛴다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select r from Reservation r where r.status = :status and r.expiresAt <= :now order by r.expiresAt asc")
    fun findExpiredPending(
        @Param("status") status: ReservationStatus,
        @Param("now") now: ZonedDateTime,
        pageable: Pageable,
    ): List<Reservation>
}
