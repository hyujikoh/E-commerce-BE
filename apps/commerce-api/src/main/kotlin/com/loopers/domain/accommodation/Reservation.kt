package com.loopers.domain.accommodation

import com.loopers.domain.BaseEntity
import com.loopers.domain.accommodation.vo.DateRange
import com.loopers.domain.accommodation.vo.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.AttributeOverride
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.Duration
import java.time.ZonedDateTime

/**
 * 예약 애그리거트 루트.
 *
 * 설계 원칙(03-class-diagram):
 * - 외부 애그리거트(객실 타입·게스트)는 ID(Long)로만 참조한다. (DDD: 다른 애그리거트는 ID 참조)
 * - 재고(DailyRoomInventory)는 직접 만지지 않는다. 차감/복구는 InventoryService 단독 책임. (B1)
 * - 상태 전이는 이 루트의 메서드로만 일어난다. setter 는 외부에 노출하지 않는다(protected).
 * - 일자별 가격 스냅샷(ReservationNightly)은 이 애그리거트 내부에서만 생성·관리한다. (B2)
 *
 * 생성은 [create] 팩토리로만 한다(불변식·파생값 계산을 한 곳에 응집).
 */
@Entity
@Table(name = "reservation")
class Reservation private constructor(
    guestId: Long,
    roomTypeId: Long,
    stayPeriod: DateRange,
    nightlySnapshots: List<ReservationNightly>,
    totalAmount: Money,
    discountAmount: Money,
    finalAmount: Money,
    appliedCouponId: Long?,
    expiresAt: ZonedDateTime,
) : BaseEntity() {

    @Column(name = "guest_id", nullable = false)
    var guestId: Long = guestId
        protected set

    @Column(name = "room_type_id", nullable = false)
    var roomTypeId: Long = roomTypeId
        protected set

    @Embedded
    var stayPeriod: DateRange = stayPeriod
        protected set

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "total_amount", nullable = false))
    @AttributeOverride(name = "currency", column = Column(name = "total_currency", nullable = false, length = 3))
    var totalAmount: Money = totalAmount
        protected set

    /** 쿠폰 할인액(미적용 시 0). 스냅샷이므로 예약 시점 값으로 고정한다. */
    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "discount_amount", nullable = false))
    @AttributeOverride(name = "currency", column = Column(name = "discount_currency", nullable = false, length = 3))
    var discountAmount: Money = discountAmount
        protected set

    /** 최종 결제 금액(= 총액 − 할인액). 스냅샷. */
    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "final_amount", nullable = false))
    @AttributeOverride(name = "currency", column = Column(name = "final_currency", nullable = false, length = 3))
    var finalAmount: Money = finalAmount
        protected set

    /** 적용된 발급 쿠폰 ID(IssuedCoupon.id). 미적용 시 null. 다른 애그리거트는 ID 로만 참조한다. */
    @Column(name = "applied_coupon_id")
    var appliedCouponId: Long? = appliedCouponId
        protected set

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private val nightlyItems: MutableList<ReservationNightly> = nightlySnapshots.toMutableList()

    /** 일자별 가격 스냅샷(불변 노출). */
    val nightly: List<ReservationNightly> get() = nightlyItems.toList()

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: ReservationStatus = ReservationStatus.PENDING
        protected set

    @Column(name = "expires_at", nullable = false)
    var expiresAt: ZonedDateTime = expiresAt
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_reason", length = 20)
    var cancelReason: CancelReason? = null
        protected set

    @Column(name = "canceled_at")
    var canceledAt: ZonedDateTime? = null
        protected set

    // ── 상태 전이 (각 메서드가 전이 조건을 자체 검증) ──

    /** 결제 성공 → 확정. */
    fun confirm() = transitionTo(ReservationStatus.CONFIRMED, allowedFrom = setOf(ReservationStatus.PENDING))

    /** 체크인 처리. */
    fun checkIn() = transitionTo(ReservationStatus.CHECKED_IN, allowedFrom = setOf(ReservationStatus.CONFIRMED))

    /** 체크아웃 처리(스케줄러 자동 전이). */
    fun checkOut() = transitionTo(ReservationStatus.CHECKED_OUT, allowedFrom = setOf(ReservationStatus.CHECKED_IN))

    /** 노쇼 처리. */
    fun markNoShow() = transitionTo(ReservationStatus.NO_SHOW, allowedFrom = setOf(ReservationStatus.CONFIRMED))

    /**
     * 취소. PENDING/CONFIRMED 에서만 가능하며, 체크인 이후에는 시스템 취소가 불가하다(P5).
     * 재고 복구는 이 메서드가 아니라 ReservationService 가 InventoryService 를 통해 수행한다(B1).
     */
    fun cancel(reason: CancelReason, now: ZonedDateTime = ZonedDateTime.now()) {
        transitionTo(ReservationStatus.CANCELLED, allowedFrom = setOf(ReservationStatus.PENDING, ReservationStatus.CONFIRMED))
        cancelReason = reason
        canceledAt = now
    }

    /** 소프트 홀드 만료(PENDING 에서만). */
    fun expire(now: ZonedDateTime = ZonedDateTime.now()) {
        transitionTo(ReservationStatus.CANCELLED, allowedFrom = setOf(ReservationStatus.PENDING))
        cancelReason = CancelReason.EXPIRED
        canceledAt = now
    }

    /** 주어진 시각 기준 만료 여부(PENDING + 만료시각 경과). */
    fun isExpired(now: ZonedDateTime = ZonedDateTime.now()): Boolean =
        status == ReservationStatus.PENDING && now.isAfter(expiresAt)

    private fun transitionTo(target: ReservationStatus, allowedFrom: Set<ReservationStatus>) {
        if (status !in allowedFrom) {
            throw CoreException(
                ErrorType.INVALID_RESERVATION_STATE,
                "현재 상태($status)에서 $target(으)로 전이할 수 없습니다.",
            )
        }
        status = target
    }

    companion object {
        /**
         * 예약을 생성한다(PENDING).
         * - 가격 스냅샷 일수가 투숙 일수와 일치해야 한다.
         * - 총액은 스냅샷 합으로 파생한다(외부가 임의 총액을 주입하지 못하게).
         * - 최종 금액은 총액 − 할인액으로 파생한다(할인액이 총액을 초과하면 Money.minus 가드가 막는다).
         * - 만료 시각은 now + holdDuration.
         */
        fun create(
            guestId: Long,
            roomTypeId: Long,
            stayPeriod: DateRange,
            nightlySnapshots: List<ReservationNightly>,
            holdDuration: Duration,
            discountAmount: Money = Money.ZERO_KRW,
            appliedCouponId: Long? = null,
            now: ZonedDateTime = ZonedDateTime.now(),
        ): Reservation {
            if (nightlySnapshots.isEmpty()) {
                throw CoreException(ErrorType.BAD_REQUEST, "예약에는 최소 1박의 가격 스냅샷이 필요합니다.")
            }
            if (nightlySnapshots.size != stayPeriod.nights) {
                throw CoreException(
                    ErrorType.BAD_REQUEST,
                    "가격 스냅샷 일수(${nightlySnapshots.size})가 투숙 일수(${stayPeriod.nights})와 다릅니다.",
                )
            }
            val total = nightlySnapshots.map { it.amount }.reduce(Money::plus)
            val finalAmount = total - discountAmount
            return Reservation(
                guestId = guestId,
                roomTypeId = roomTypeId,
                stayPeriod = stayPeriod,
                nightlySnapshots = nightlySnapshots,
                totalAmount = total,
                discountAmount = discountAmount,
                finalAmount = finalAmount,
                appliedCouponId = appliedCouponId,
                expiresAt = now.plus(holdDuration),
            )
        }
    }
}
