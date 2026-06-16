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
 * 예약 생애주기를 지휘하는 서비스. (이번 슬라이스는 생성까지)
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

    companion object {
        /** 소프트 홀드 시간(P4: 10분). */
        val HOLD_DURATION: Duration = Duration.ofMinutes(10)
    }
}
