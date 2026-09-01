package com.loopers.interfaces.api.payment

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Payment V1 API", description = "결제 API")
interface PaymentV1ApiSpec {
    @Operation(
        summary = "결제 요청",
        description = "PENDING 예약의 결제를 PG 에 요청한다. 동일 예약의 진행 중 결제가 있으면 그것을 반환한다(멱등). " +
            "PG 장애·거절은 예외가 아니라 결제 상태(REQUEST_FAILED 등)로 담아 정상 응답한다.",
    )
    fun pay(request: PaymentV1Dto.PayRequest): ApiResponse<PaymentV1Dto.PaymentResponse>

    @Operation(
        summary = "PG 결제 결과 콜백",
        description = "PG 시뮬레이터가 결제 처리 완료 시 호출한다. 본문을 신뢰하지 않고 PG 재조회로 검증 후 반영한다.",
    )
    fun callback(request: PaymentV1Dto.CallbackRequest): ApiResponse<Any>
}
