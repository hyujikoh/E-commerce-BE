package com.loopers.domain.accommodation

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.Check
import java.time.LocalDate

/**
 * 일자별 재고 — 특정 (객실 타입, 날짜)의 잔여 객실 수량. 더블부킹 방지의 최후 방어선.
 *
 * 설계(04-erd)는 (room_type_id, date) 복합 PK 를 명시하나, 본 프로젝트의 BaseEntity 컨벤션(surrogate PK)을 따르기 위해
 * surrogate id + UNIQUE(room_type_id, date) 로 "한 (객실타입,날짜) 1행" 보장을 동일하게 달성한다.
 *
 * 차감/복구는 엔티티 상태 변경이 아니라 InventoryService 가 호출하는 원자적 UPDATE 로 수행한다
 * (DailyRoomInventoryRepository.decrementIfAvailable). 동시성 보장을 SQL 한 줄에 응집하기 위함.
 * remaining >= 0 은 CHECK 제약 + UPDATE 의 `WHERE remaining > 0` 두 겹으로 방어한다.
 */
@Entity
@Table(
    name = "daily_room_inventory",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_inventory_room_type_date", columnNames = ["room_type_id", "date"]),
    ],
)
@Check(constraints = "remaining >= 0")
class DailyRoomInventory(
    roomTypeId: Long,
    date: LocalDate,
    remaining: Int,
) : BaseEntity() {
    @Column(name = "room_type_id", nullable = false)
    var roomTypeId: Long = roomTypeId
        protected set

    @Column(name = "date", nullable = false)
    var date: LocalDate = date
        protected set

    @Column(name = "remaining", nullable = false)
    var remaining: Int = remaining
        protected set

    init {
        if (remaining < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "재고는 음수일 수 없습니다.")
        }
    }
}
