package com.loopers.infrastructure.payment.pg

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.springframework.stereotype.Component

/**
 * 서킷브레이커 계측 지점. 예외를 삼키지 않고 그대로 전파해야 실패가 서킷에 기록되므로,
 * 결과 해석(성공/거절/불명 분류)은 [PgSimulatorGateway] 가 담당하고 여기는 위임만 한다.
 *
 * 서킷 분리 이유: 결제 생성은 PG 스펙상 정상 상태에서도 40% 가 실패한다 — 조회까지 같은 서킷에
 * 묶으면 생성 실패율이 조회를 차단한다. 생성(pg-payment)과 조회(pg-query)를 분리한다.
 */
@Component
class PgSimulatorCircuitCaller(
    private val pgSimulatorClient: PgSimulatorClient,
) {
    @CircuitBreaker(name = CIRCUIT_PAYMENT)
    fun requestPayment(
        userId: String,
        request: PgSimulatorDto.PaymentRequest,
    ): PgSimulatorDto.Response<PgSimulatorDto.TransactionResponse> = pgSimulatorClient.requestPayment(userId, request)

    @CircuitBreaker(name = CIRCUIT_QUERY)
    fun getTransaction(
        userId: String,
        transactionKey: String,
    ): PgSimulatorDto.Response<PgSimulatorDto.TransactionDetailResponse> = pgSimulatorClient.getTransaction(
        userId,
        transactionKey,
    )

    @CircuitBreaker(name = CIRCUIT_QUERY)
    fun getTransactionsByOrderId(
        userId: String,
        orderId: String,
    ): PgSimulatorDto.Response<PgSimulatorDto.OrderResponse> = pgSimulatorClient.getTransactionsByOrderId(userId, orderId)

    companion object {
        const val CIRCUIT_PAYMENT = "pg-payment"
        const val CIRCUIT_QUERY = "pg-query"
    }
}
