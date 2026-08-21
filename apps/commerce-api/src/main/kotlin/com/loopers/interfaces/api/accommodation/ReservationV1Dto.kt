package com.loopers.interfaces.api.accommodation

import com.loopers.application.accommodation.ReservationInfo
import com.loopers.domain.accommodation.CancelReason
import com.loopers.domain.accommodation.ReservationStatus
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.time.LocalDate
import java.time.ZonedDateTime

class ReservationV1Dto {
    @Schema(description = "예약 생성 요청")
    data class CreateRequest(
        // NOTE: 이번 슬라이스는 인증 미연동. 실제로는 @LoopersAuth 로 게스트를 식별한다(후속).
        @field:NotNull
        @Schema(description = "게스트(회원) ID")
        val guestId: Long,
        @field:NotNull
        @Schema(description = "객실 타입 ID")
        val roomTypeId: Long,
        @field:NotNull
        @Schema(description = "체크인 날짜 (YYYY-MM-DD)")
        val checkIn: LocalDate,
        @field:NotNull
        @Schema(description = "체크아웃 날짜 (YYYY-MM-DD)")
        val checkOut: LocalDate,
        @Schema(description = "적용할 발급 쿠폰 ID(내 쿠폰의 issuedCouponId). 미적용 시 생략", example = "42")
        val couponId: Long? = null,
    )

    @Schema(description = "예약 취소 요청")
    data class CancelRequest(
        // NOTE: 이번 슬라이스는 인증 미연동. 실제로는 @LoopersAuth 로 게스트를 식별한다(후속).
        @field:NotNull
        @Schema(description = "게스트(회원) ID — 본인 예약 검증용")
        val guestId: Long,
    )

    @Schema(description = "예약 응답")
    data class ReservationResponse(
        val id: Long,
        val guestId: Long,
        val roomTypeId: Long,
        val checkIn: LocalDate,
        val checkOut: LocalDate,
        val status: ReservationStatus,
        val totalAmount: Long,
        val discountAmount: Long,
        val finalAmount: Long,
        val appliedCouponId: Long?,
        val currency: String,
        val expiresAt: ZonedDateTime,
        val cancelReason: CancelReason?,
        val canceledAt: ZonedDateTime?,
        val nightly: List<NightlyResponse>,
    ) {
        data class NightlyResponse(val date: LocalDate, val amount: Long)

        companion object {
            fun from(info: ReservationInfo): ReservationResponse =
                ReservationResponse(
                    id = info.id,
                    guestId = info.guestId,
                    roomTypeId = info.roomTypeId,
                    checkIn = info.checkIn,
                    checkOut = info.checkOut,
                    status = info.status,
                    totalAmount = info.totalAmount,
                    discountAmount = info.discountAmount,
                    finalAmount = info.finalAmount,
                    appliedCouponId = info.appliedCouponId,
                    currency = info.currency,
                    expiresAt = info.expiresAt,
                    cancelReason = info.cancelReason,
                    canceledAt = info.canceledAt,
                    nightly = info.nightly.map { NightlyResponse(it.date, it.amount) },
                )
        }
    }
}
