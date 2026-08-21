package com.loopers.domain.property

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

/**
 * 객실 타입 — 숙소에 속하며, 일자별 재고(daily_room_inventory)·요금(daily_room_rate)의 기준 단위.
 * 다른 애그리거트(Property)는 id 로만 참조한다(기존 Reservation → RoomType 참조 방식과 동일).
 *
 * idx_room_type_property_capacity: 검색이 property → room_type 조인 후 capacity 로 필터하므로
 * (property_id, capacity) 복합으로 커버한다.
 */
@Entity
@Table(
    name = "room_type",
    indexes = [
        Index(name = "idx_room_type_property_capacity", columnList = "property_id, capacity"),
    ],
)
class RoomType(
    propertyId: Long,
    name: String,
    capacity: Int,
) : BaseEntity() {
    @Column(name = "property_id", nullable = false)
    var propertyId: Long = propertyId
        protected set

    @Column(name = "name", nullable = false)
    var name: String = name
        protected set

    /** 최대 숙박 인원 */
    @Column(name = "capacity", nullable = false)
    var capacity: Int = capacity
        protected set

    init {
        if (name.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "객실 타입 이름은 비어 있을 수 없습니다.")
        }
        if (capacity < 1) {
            throw CoreException(ErrorType.BAD_REQUEST, "수용 인원은 1명 이상이어야 합니다.")
        }
    }
}
