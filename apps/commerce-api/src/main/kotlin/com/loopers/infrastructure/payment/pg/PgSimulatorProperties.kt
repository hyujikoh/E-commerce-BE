package com.loopers.infrastructure.payment.pg

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "pg-simulator")
data class PgSimulatorProperties(
    /** PG 시뮬레이터 베이스 URL. */
    val baseUrl: String,
    /** 결제 결과 콜백을 받을 우리 쪽 엔드포인트. PG 규격상 http://localhost:8080 프리픽스가 강제된다. */
    val callbackUrl: String,
)
