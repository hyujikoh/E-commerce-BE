package com.loopers.domain.accommodation

import com.loopers.domain.accommodation.vo.DateRange
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

/**
 * 예약 생애주기를 지휘하는 서비스. (이번 슬라이스는 생성까지)
 *
 * create 흐름(시퀀스 1 일부):
 *  1) 투숙 일자별 요금을 조회해 가격 스냅샷을 만든다(B2: 가격 잠금).
 *  2) InventoryService 로 일자별 재고를 차감한다(B1: 재고 단독 책임, 더블부킹 방지).
 *  3) PENDING(10분 소프트 홀드) 예약을 생성·저장한다.
 *
 * 1~3 을 한 트랜잭션으로 묶어 재고와 예약의 일관성을 보장한다(B5).
 */
@Component
class ReservationService(
    private val reservationRepository: ReservationRepository,
    private val dailyRoomRateRepository: DailyRoomRateRepository,
    private val inventoryService: InventoryService,
) {
    @Transactional
    fun create(guestId: Long, roomTypeId: Long, stayPeriod: DateRange): Reservation {
        val dates = stayPeriod.datesExclusive()

        // 1) 가격 스냅샷 — 모든 투숙 일자에 요금이 존재해야 한다.
        val rates = dailyRoomRateRepository.findByRoomTypeAndDates(roomTypeId, dates)
        if (rates.size != dates.size) {
            throw CoreException(ErrorType.RATE_NOT_FOUND, "요청 기간의 모든 일자에 요금 정보가 존재하지 않습니다.")
        }
        val amountByDate = rates.associate { it.date to it.amount }
        val nightly = dates.map { date -> ReservationNightly(date, amountByDate.getValue(date)) }

        // 2) 재고 차감 (더블부킹 방지)
        inventoryService.reserve(roomTypeId, dates)

        // 3) 예약 생성 (PENDING)
        val reservation = Reservation.create(
            guestId = guestId,
            roomTypeId = roomTypeId,
            stayPeriod = stayPeriod,
            nightlySnapshots = nightly,
            holdDuration = HOLD_DURATION,
        )
        return reservationRepository.save(reservation)
    }

    companion object {
        /** 소프트 홀드 시간(P4: 10분). */
        val HOLD_DURATION: Duration = Duration.ofMinutes(10)
    }
}
