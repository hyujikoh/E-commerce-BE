package com.loopers.infrastructure.accommodation

import com.loopers.domain.accommodation.DailyRoomInventory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface DailyRoomInventoryJpaRepository : JpaRepository<DailyRoomInventory, Long> {
    /**
     * 원자적 차감 — 잔여가 1 이상인 행만 -1. 동시 요청 중 한쪽만 1행을 변경하고 나머지는 0행을 변경한다.
     * @return 변경된 행 수(0 또는 1).
     */
    @Modifying(clearAutomatically = true)
    @Query(
        "update DailyRoomInventory i set i.remaining = i.remaining - 1 " +
            "where i.roomTypeId = :roomTypeId and i.date = :date and i.remaining > 0",
    )
    fun decrementIfAvailable(@Param("roomTypeId") roomTypeId: Long, @Param("date") date: LocalDate): Int

    /**
     * 원자적 복구 — 예약 취소/만료 시 점유했던 재고를 +1 되돌린다.
     * clearAutomatically 를 쓰지 않는다 — 취소/만료 트랜잭션은 Reservation 의 상태 전이(dirty)를
     * 커밋 시점에 flush 해야 하는데, clear 가 그 변경을 유실시킨다. 이 트랜잭션에서 재고 엔티티를
     * 다시 읽는 경로가 없으므로 stale 위험도 없다.
     * @return 변경된 행 수(0=행 없음, 1=복구).
     */
    @Modifying
    @Query(
        "update DailyRoomInventory i set i.remaining = i.remaining + 1 " +
            "where i.roomTypeId = :roomTypeId and i.date = :date",
    )
    fun increment(@Param("roomTypeId") roomTypeId: Long, @Param("date") date: LocalDate): Int
}
