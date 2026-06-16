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
 * 예약 1건의 일자별 가격 스냅샷 (B2: 가격 잠금).
 *
 * Reservation 애그리거트 내부 엔티티다. 외부에서 직접 생성·수정하지 않고 항상 Reservation 루트를 통해 다룬다.
 * (별도 Repository 를 두지 않는 이유 — 애그리거트 경계 안에 있기 때문.)
 *
 * 설계(04-erd)는 (reservation_id, date) 복합 PK 이나, BaseEntity 컨벤션(surrogate PK)을 따르므로
 * UNIQUE(reservation_id, date) 로 "예약 1건 + 한 일자 1행" 보장을 동일하게 둔다(DailyRoomInventory/Rate 와 대칭).
 * reservation_id 컬럼은 Reservation 의 @OneToMany @JoinColumn 으로 생성된다.
 * amount 는 Money 의 기본 컬럼명(amount, currency)을 그대로 사용한다.
 */
@Entity
@Table(
    name = "reservation_nightly",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_reservation_nightly_date", columnNames = ["reservation_id", "date"]),
    ],
)
class ReservationNightly(
    date: LocalDate,
    amount: Money,
) : BaseEntity() {
    @Column(name = "date", nullable = false)
    var date: LocalDate = date
        protected set

    @Embedded
    var amount: Money = amount
        protected set
}
