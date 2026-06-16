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
}
