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
}
