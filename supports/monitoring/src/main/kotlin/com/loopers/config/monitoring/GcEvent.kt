package com.loopers.config.monitoring

import java.time.Instant

/**
 * GC 1회 발생 정보의 불변 스냅샷.
 * JMX notification 스레드에서 생성되어 히스토리/엔드포인트로 전달된다.
 */
data class GcEvent(
    val id: Long,
    val gcName: String,
    val action: String,
    val cause: String,
    val timestamp: Instant,
    val durationMillis: Long,
    val reclaimedBytes: Long,
    val poolChanges: List<PoolChange>,
)

/**
 * GC 전후로 사용량이 변한 메모리 풀의 변화량. 변화가 없는 풀은 포함하지 않는다.
 */
data class PoolChange(
    val pool: String,
    val beforeUsedBytes: Long,
    val afterUsedBytes: Long,
) {
    val deltaBytes: Long get() = afterUsedBytes - beforeUsedBytes
}
