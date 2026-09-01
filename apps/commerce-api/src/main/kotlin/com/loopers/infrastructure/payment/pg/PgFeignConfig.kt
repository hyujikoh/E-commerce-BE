package com.loopers.infrastructure.payment.pg

import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.context.annotation.Configuration

/** Feign 클라이언트 활성화. 대상을 명시해 클래스패스 전체 스캔을 피한다. */
@Configuration
@EnableFeignClients(clients = [PgSimulatorClient::class])
class PgFeignConfig
