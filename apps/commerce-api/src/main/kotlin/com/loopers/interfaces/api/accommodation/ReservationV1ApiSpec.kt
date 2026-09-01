package com.loopers.interfaces.api.accommodation

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Reservation V1 API", description = "숙박 예약 API")
interface ReservationV1ApiSpec {
    @Operation(
        summary = "예약 생성",
        description = "객실 타입과 기간을 받아 결제 대기(PENDING) 예약을 생성하고 일자별 재고를 점유한다.",
    )
    fun create(request: ReservationV1Dto.CreateRequest): ApiResponse<ReservationV1Dto.ReservationResponse>

    @Operation(
        summary = "예약 확정",
        description = "결제 성공 시 예약을 확정한다(PENDING → CONFIRMED). 홀드가 만료된 예약이면 취소 정리 후 실패를 반환한다.",
    )
    fun confirm(reservationId: Long): ApiResponse<ReservationV1Dto.ReservationResponse>

    @Operation(
        summary = "예약 취소",
        description = "본인의 PENDING/CONFIRMED 예약을 취소하고 점유한 재고를 복구한다. 체크인 이후에는 취소할 수 없다.",
    )
    fun cancel(reservationId: Long, request: ReservationV1Dto.CancelRequest): ApiResponse<ReservationV1Dto.ReservationResponse>
}
