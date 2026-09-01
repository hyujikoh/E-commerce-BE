package com.loopers.infrastructure.payment.pg

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam

/**
 * PG 시뮬레이터 HTTP 클라이언트. 타임아웃은 application.yml 의
 * spring.cloud.openfeign.client.config.pg-simulator 에서 관리한다.
 *
 * 결제 생성은 100~500ms 지연 + 40% 확률 500 응답(거래 생성 전 단계)이 스펙이므로
 * read-timeout 은 그 지연의 여유(수 배)로 잡는다 — 타임아웃 = "거래 생성 여부 불명"으로 다룬다.
 */
@FeignClient(name = "pg-simulator", url = "\${pg-simulator.base-url}")
interface PgSimulatorClient {
    @PostMapping("/api/v1/payments")
    fun requestPayment(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestBody request: PgSimulatorDto.PaymentRequest,
    ): PgSimulatorDto.Response<PgSimulatorDto.TransactionResponse>

    @GetMapping("/api/v1/payments/{transactionKey}")
    fun getTransaction(
        @RequestHeader("X-USER-ID") userId: String,
        @PathVariable("transactionKey") transactionKey: String,
    ): PgSimulatorDto.Response<PgSimulatorDto.TransactionDetailResponse>

    @GetMapping("/api/v1/payments")
    fun getTransactionsByOrderId(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestParam("orderId") orderId: String,
    ): PgSimulatorDto.Response<PgSimulatorDto.OrderResponse>
}
