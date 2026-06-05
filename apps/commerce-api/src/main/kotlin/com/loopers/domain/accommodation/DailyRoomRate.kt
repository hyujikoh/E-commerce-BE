package com.loopers.domain.accommodation

import com.loopers.domain.BaseEntity
import com.loopers.domain.accommodation.vo.Money
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

/**
 * 일자별 요금 — 특정 (객실 타입, 날짜)의 1박 요금. 검색 가격 합산 및 PENDING 가격 스냅샷의 원천(B2).
 *
 * 설계의 (room_type_id, date) 복합 PK 는 surrogate id + UNIQUE 로 동일 보장한다(DailyRoomInventory 와 동일 결정).
 * amount 는 Money 의 기본 컬럼명(amount, currency)을 그대로 사용한다.
 */
@Entity
@Table(
    name = "daily_room_rate",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_rate_room_type_date", columnNames = ["room_type_id", "date"]),
    ],
)
class DailyRoomRate(
    roomTypeId: Long,
    date: LocalDate,
    amount: Money,
) : BaseEntity() {
    @Column(name = "room_type_id", nullable = false)
    var roomTypeId: Long = roomTypeId
        protected set

    @Column(name = "date", nullable = false)
    var date: LocalDate = date
        protected set

    @Embedded
    var amount: Money = amount
        protected set
}
