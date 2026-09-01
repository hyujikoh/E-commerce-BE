package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PaymentInfo
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentStatus
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

class PaymentV1Dto {
    @Schema(description = "결제 요청")
    data class PayRequest(
        // NOTE: 이번 슬라이스는 인증 미연동. 실제로는 @LoopersAuth 로 게스트를 식별한다(후속).
        @field:NotNull
        @Schema(description = "게스트(회원) ID")
        val guestId: Long,
        @field:NotNull
        @Schema(description = "결제할 예약 ID")
        val reservationId: Long,
        @field:NotNull
        @Schema(description = "카드사")
        val cardType: CardType,
        @field:NotBlank
        @Schema(description = "카드 번호 (xxxx-xxxx-xxxx-xxxx)", example = "1234-5678-9012-3456")
        val cardNo: String,
    )

    /** PG 시뮬레이터가 결제 처리 완료 시 POST 하는 콜백 본문(TransactionInfo). 검증은 PG 재조회로 한다. */
    @Schema(description = "PG 결제 결과 콜백")
    data class CallbackRequest(
        @field:NotBlank
        val transactionKey: String,
        @field:NotBlank
        val orderId: String,
        val cardType: String? = null,
        val cardNo: String? = null,
        val amount: Long? = null,
        val status: String? = null,
        val reason: String? = null,
    )

    @Schema(description = "결제 응답")
    data class PaymentResponse(
        val id: Long,
        val reservationId: Long,
        val orderId: String,
        val cardType: CardType,
        val status: PaymentStatus,
        val amount: Long,
        val currency: String,
        val transactionKey: String?,
        val failureReason: String?,
    ) {
        companion object {
            fun from(info: PaymentInfo): PaymentResponse =
                PaymentResponse(
                    id = info.id,
                    reservationId = info.reservationId,
                    orderId = info.orderId,
                    cardType = info.cardType,
                    status = info.status,
                    amount = info.amount,
                    currency = info.currency,
                    transactionKey = info.transactionKey,
                    failureReason = info.failureReason,
                )
        }
    }
}
