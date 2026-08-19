package com.loopers.config.monitoring

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class JvmMemoryEndpointTest {
    private val history = GcEventHistory()
    private val endpoint = JvmMemoryEndpoint(history)

    @DisplayName("report는")
    @Nested
    inner class Report {
        @DisplayName("heap/non-heap 사용량과 메모리 풀, GC 컬렉터 정보를 채워 반환한다")
        @Test
        fun returnsMemoryAndGcInfo() {
            val report = endpoint.report()

            assertThat(report.heap.used).isGreaterThan(0)
            assertThat(report.nonHeap.used).isGreaterThan(0)
            assertThat(report.pools.map { it.type }).contains("HEAP", "NON_HEAP")
            assertThat(report.pools.map { it.name }).anyMatch { it.contains("Metaspace") }
            assertThat(report.gc).isNotEmpty()
        }

        @DisplayName("max가 없는 풀(max = -1)의 usedRatio는 null, 있는 풀은 0~1 사이 값이다")
        @Test
        fun calculatesUsedRatioOnlyWhenMaxExists() {
            val report = endpoint.report()

            report.pools.forEach { pool ->
                if (pool.max > 0) {
                    assertThat(pool.usedRatio).isBetween(0.0, 1.0)
                } else {
                    assertThat(pool.usedRatio).isNull()
                }
            }
        }

        @DisplayName("히스토리의 최근 GC 이벤트를 포함한다")
        @Test
        fun includesRecentGcEvents() {
            val event = GcEvent(
                id = 1,
                gcName = "G1 Young Generation",
                action = "end of minor GC",
                cause = "G1 Evacuation Pause",
                timestamp = Instant.now(),
                durationMillis = 5,
                reclaimedBytes = 1_024,
                poolChanges = emptyList(),
            )
            history.add(event)

            assertThat(endpoint.report().recentGcEvents).containsExactly(event)
        }
    }
}
