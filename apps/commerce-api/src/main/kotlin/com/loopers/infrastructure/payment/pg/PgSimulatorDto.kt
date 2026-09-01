package com.loopers.infrastructure.payment.pg

import com.loopers.domain.payment.PgTransaction
import com.loopers.domain.payment.PgTransactionStatus

/** PG 시뮬레이터 API 계약. 응답은 { meta, data } 래퍼로 감싸져 온다. */
object PgSimulatorDto {
    data class Response<T>(
        val meta: Meta,
        val data: T?,
    ) {
        data class Meta(
            val result: String,
            val errorCode: String?,
            val message: String?,
        )
    }

    data class PaymentRequest(
        val orderId: String,
        val cardType: String,
        val cardNo: String,
        val amount: Long,
        val callbackUrl: String,
    )

    data class TransactionResponse(
        val transactionKey: String,
        val status: String,
        val reason: String?,
    )

    data class TransactionDetailResponse(
        val transactionKey: String,
        val orderId: String,
        val cardType: String,
        val cardNo: String,
        val amount: Long,
        val status: String,
        val reason: String?,
    ) {
        fun toPgTransaction(): PgTransaction =
            PgTransaction(
                transactionKey = transactionKey,
                orderId = orderId,
                status = PgTransactionStatus.valueOf(status),
                reason = reason,
            )
    }

    data class OrderResponse(
        val orderId: String,
        val transactions: List<TransactionResponse>,
    ) {
        fun toPgTransactions(): List<PgTransaction> =
            transactions.map {
                PgTransaction(
                    transactionKey = it.transactionKey,
                    orderId = orderId,
                    status = PgTransactionStatus.valueOf(it.status),
                    reason = it.reason,
                )
            }
    }
}
