package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PaymentFacade
import com.loopers.interfaces.api.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/payments")
class PaymentV1Controller(
    private val paymentFacade: PaymentFacade,
) : PaymentV1ApiSpec {
    @PostMapping
    override fun pay(
        @Valid @RequestBody request: PaymentV1Dto.PayRequest,
    ): ApiResponse<PaymentV1Dto.PaymentResponse> {
        return paymentFacade.pay(
            reservationId = request.reservationId,
            guestId = request.guestId,
            cardType = request.cardType,
            cardNo = request.cardNo,
        )
            .let { PaymentV1Dto.PaymentResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    // 콜백은 best-effort — 처리 불가 건도 200 으로 응답한다(PG 는 재시도하지 않고, 유실 건은 상태 동기화가 복구).
    @PostMapping("/callback")
    override fun callback(
        @Valid @RequestBody request: PaymentV1Dto.CallbackRequest,
    ): ApiResponse<Any> {
        paymentFacade.handleCallback(orderId = request.orderId, transactionKey = request.transactionKey)
        return ApiResponse.success()
    }
}
