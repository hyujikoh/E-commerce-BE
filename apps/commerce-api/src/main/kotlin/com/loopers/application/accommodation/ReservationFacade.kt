package com.loopers.application.accommodation

import com.loopers.domain.accommodation.CancelReason
import com.loopers.domain.accommodation.ReservationService
import com.loopers.domain.accommodation.vo.DateRange
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class ReservationFacade(
    private val reservationService: ReservationService,
) {
    fun create(
        guestId: Long,
        roomTypeId: Long,
        checkIn: LocalDate,
        checkOut: LocalDate,
        couponId: Long? = null,
    ): ReservationInfo {
        val reservation = reservationService.create(
            guestId = guestId,
            roomTypeId = roomTypeId,
            stayPeriod = DateRange(checkIn, checkOut),
            couponId = couponId,
        )
        return ReservationInfo.from(reservation)
    }

    /**
     * 결제 성공 → 예약 확정. 재진입 시점의 만료 검사(P4)로 expire 를 먼저 호출한다.
     * expire 와 confirm 을 별도 트랜잭션으로 분리한 이유: 만료 정리(취소+재고·쿠폰 복구)를 커밋한 채로
     * 확정 실패를 응답해야 하는데, 한 트랜잭션에서 예외를 던지면 정리까지 롤백되기 때문이다.
     */
    fun confirm(reservationId: Long): ReservationInfo {
        if (reservationService.expire(reservationId)) {
            throw CoreException(ErrorType.INVALID_RESERVATION_STATE, "홀드가 만료되어 예약이 취소되었습니다. 다시 예약해주세요.")
        }
        return ReservationInfo.from(reservationService.confirm(reservationId))
    }

    /** 사용자 예약 취소. 만료 시각이 지난 PENDING 이면 서비스가 EXPIRED 사유로 정리한 결과를 반환한다. */
    fun cancel(reservationId: Long, guestId: Long): ReservationInfo =
        ReservationInfo.from(reservationService.cancel(reservationId, guestId, CancelReason.USER_REQUEST))
}
