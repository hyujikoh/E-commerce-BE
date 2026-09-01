package com.loopers.application.payment

import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentStatus

data class PaymentInfo(
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
        fun from(payment: Payment): PaymentInfo =
            PaymentInfo(
                id = payment.id,
                reservationId = payment.reservationId,
                orderId = payment.orderId,
                cardType = payment.cardType,
                status = payment.status,
                amount = payment.amount.amount,
                currency = payment.amount.currency,
                transactionKey = payment.transactionKey,
                failureReason = payment.failureReason,
            )
    }
}
