package com.loopers.domain.accommodation

import com.loopers.domain.accommodation.vo.DateRange
import com.loopers.domain.accommodation.vo.Money
import com.loopers.domain.coupon.CouponService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.ZonedDateTime

/**
 * 예약 생애주기를 지휘하는 서비스.
 *
 * create 흐름(시퀀스 1):
 *  1) 투숙 일자별 요금을 조회해 가격 스냅샷을 만든다(B2: 가격 잠금).
 *  2) 쿠폰이 있으면 사용 처리하여 할인액을 확정한다(단일 사용 보장, 무효 쿠폰 시 fail-fast).
 *  3) InventoryService 로 일자별 재고를 차감한다(B1: 재고 단독 책임, 더블부킹 방지).
 *  4) PENDING(10분 소프트 홀드) 예약을 할인/최종금액 스냅샷과 함께 생성·저장한다.
 *
 * 1~4 를 한 트랜잭션으로 묶어 쿠폰·재고·예약의 정합성을 보장한다(하나라도 실패하면 전부 롤백, B5).
 *
 * 락 순서: 쿠폰 사용(2) → 재고 차감(3) 으로 고정한다.
 *  - 단일 호출자에서 항상 같은 순서를 지켜 쿠폰 row 락과 다일자 재고 row 락 사이의 교차 대기(데드락)를 차단한다.
 *  - 무효 쿠폰을 재고 점유 이전에 실패시켜 불필요한 재고 락 획득을 줄인다.
 *
 * 상태 전이(confirm/cancel/expire)의 동시성 가드: 예약 행을 FOR UPDATE 로 잠근 뒤(findWithLock)
 * 최신 상태에서 애그리거트의 전이 메서드를 호출한다. 확정 vs 만료 같은 경쟁은 행 잠금으로 직렬화되고,
 * 늦게 도착한 쪽은 갱신된 상태를 보고 전이 가드(INVALID_RESERVATION_STATE) 또는 no-op 으로 정리된다.
 *
 * 취소/만료의 자원 복구: 재고는 항상 복구하고, 쿠폰은 PENDING 에서 취소·만료된 경우만 복구한다
 * (결제가 완료되지 않았으므로). CONFIRMED 취소의 쿠폰·환불 정책은 후속 환불 도메인에서 다룬다.
 */
@Component
class ReservationService(
    private val reservationRepository: ReservationRepository,
    private val dailyRoomRateRepository: DailyRoomRateRepository,
    private val inventoryService: InventoryService,
    private val couponService: CouponService,
) {
    @Transactional
    fun create(
        guestId: Long,
        roomTypeId: Long,
        stayPeriod: DateRange,
        couponId: Long? = null,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): Reservation {
        val dates = stayPeriod.datesExclusive()

        // 1) 가격 스냅샷 — 모든 투숙 일자에 요금이 존재해야 한다.
        val rates = dailyRoomRateRepository.findByRoomTypeAndDates(roomTypeId, dates)
        if (rates.size != dates.size) {
            throw CoreException(ErrorType.RATE_NOT_FOUND, "요청 기간의 모든 일자에 요금 정보가 존재하지 않습니다.")
        }
        val amountByDate = rates.associate { it.date to it.amount }
        val nightly = dates.map { date -> ReservationNightly(date, amountByDate.getValue(date)) }
        val total = nightly.map { it.amount }.reduce(Money::plus)

        // 2) 쿠폰 사용 (있을 때만). use() 가 예약 트랜잭션에 합류 → 실패 시 전체 롤백.
        val discount = if (couponId != null) {
            couponService.use(guestId, couponId, total, now)
        } else {
            Money.ZERO_KRW
        }

        // 3) 재고 차감 (더블부킹 방지)
        inventoryService.reserve(roomTypeId, dates)

        // 4) 예약 생성 (PENDING) — 할인 전/할인/최종 금액 스냅샷 포함
        val reservation = Reservation.create(
            guestId = guestId,
            roomTypeId = roomTypeId,
            stayPeriod = stayPeriod,
            nightlySnapshots = nightly,
            holdDuration = HOLD_DURATION,
            discountAmount = discount,
            appliedCouponId = couponId,
            now = now,
        )
        return reservationRepository.save(reservation)
    }

    /** 결제 성공 → 확정(PENDING → CONFIRMED). 만료 검사는 호출자(Facade)가 [expire] 선행 호출로 수행한다. */
    @Transactional
    fun confirm(reservationId: Long): Reservation {
        val reservation = findWithLockOrThrow(reservationId)
        reservation.confirm()
        return reservation
    }

    /**
     * 예약 취소(D-1: PENDING/CONFIRMED 에서만). 재고를 복구하고, PENDING 취소는 쿠폰도 복구한다.
     * 이미 만료 시각이 지난 PENDING 이면 사용자 요청이어도 EXPIRED 사유로 정리한다 — CancelReason 은
     * 정산·환불 분기 키이므로 실제 원인을 기록한다.
     */
    @Transactional
    fun cancel(
        reservationId: Long,
        guestId: Long,
        reason: CancelReason,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): Reservation {
        val reservation = findWithLockOrThrow(reservationId)
        if (reservation.guestId != guestId) {
            throw CoreException(ErrorType.RESERVATION_NOT_OWNED)
        }
        if (reservation.isExpired(now)) {
            doExpire(reservation, now)
            return reservation
        }
        val wasPending = reservation.status == ReservationStatus.PENDING
        reservation.cancel(reason, now)
        inventoryService.release(reservation.roomTypeId, reservation.stayPeriod.datesExclusive())
        if (wasPending) {
            reservation.appliedCouponId?.let { couponService.restore(it) }
        }
        return reservation
    }

    /**
     * 소프트 홀드 만료 처리. 스케줄러와 결제 재진입(Facade.confirm)이 같은 이 메서드를 호출한다.
     * @return 만료 처리 여부. 만료 대상이 아니면(PENDING 아님/기한 미도래/없음) 아무것도 바꾸지 않고 false.
     */
    @Transactional
    fun expire(reservationId: Long, now: ZonedDateTime = ZonedDateTime.now()): Boolean {
        val reservation = reservationRepository.findWithLock(reservationId) ?: return false
        if (!reservation.isExpired(now)) {
            return false
        }
        doExpire(reservation, now)
        return true
    }

    /**
     * 만료 시각이 지난 PENDING 예약을 일괄 만료 처리한다(스케줄러 진입점).
     * FOR UPDATE SKIP LOCKED 스캔으로 다중 인스턴스가 같은 행을 중복 처리하지 않는다.
     * @return 만료 처리한 건수.
     */
    @Transactional
    fun expireOverdue(now: ZonedDateTime = ZonedDateTime.now(), limit: Int = DEFAULT_EXPIRE_BATCH_SIZE): Int {
        val targets = reservationRepository.findExpiredPendingWithLock(now, limit)
        targets.forEach { doExpire(it, now) }
        return targets.size
    }

    /** 만료 확정 후의 공통 정리 — 상태 전이 + 재고 복구 + 쿠폰 복구(PENDING 이었으므로 항상). */
    private fun doExpire(reservation: Reservation, now: ZonedDateTime) {
        reservation.expire(now)
        inventoryService.release(reservation.roomTypeId, reservation.stayPeriod.datesExclusive())
        reservation.appliedCouponId?.let { couponService.restore(it) }
    }

    private fun findWithLockOrThrow(reservationId: Long): Reservation =
        reservationRepository.findWithLock(reservationId)
            ?: throw CoreException(ErrorType.RESERVATION_NOT_FOUND)

    companion object {
        /** 소프트 홀드 시간(P4: 10분). */
        val HOLD_DURATION: Duration = Duration.ofMinutes(10)

        /** 만료 스캔 1회 최대 처리 건수 — 트랜잭션과 잠금 범위를 제한한다. */
        const val DEFAULT_EXPIRE_BATCH_SIZE: Int = 100
    }
}
