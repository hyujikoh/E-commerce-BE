package com.loopers.config.monitoring

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class GcEventHistoryTest {
    private fun gcEvent(id: Long): GcEvent = GcEvent(
        id = id,
        gcName = "G1 Young Generation",
        action = "end of minor GC",
        cause = "G1 Evacuation Pause",
        timestamp = Instant.ofEpochMilli(id),
        durationMillis = 5,
        reclaimedBytes = 1_024,
        poolChanges = emptyList(),
    )

    @DisplayName("GC 이벤트 히스토리는")
    @Nested
    inner class Describe {
        @DisplayName("비어 있으면 빈 목록을 반환한다")
        @Test
        fun returnsEmptyList_whenNoEvents() {
            val history = GcEventHistory()

            assertThat(history.snapshot()).isEmpty()
        }

        @DisplayName("최신 이벤트부터 순서로 반환한다")
        @Test
        fun returnsNewestFirst() {
            val history = GcEventHistory(capacity = 10)
            history.add(gcEvent(1))
            history.add(gcEvent(2))
            history.add(gcEvent(3))

            assertThat(history.snapshot().map { it.id }).containsExactly(3, 2, 1)
        }

        @DisplayName("용량을 초과하면 가장 오래된 이벤트를 제거한다")
        @Test
        fun evictsOldest_whenCapacityExceeded() {
            val history = GcEventHistory(capacity = 3)
            (1L..5L).forEach { history.add(gcEvent(it)) }

            assertThat(history.snapshot().map { it.id }).containsExactly(5, 4, 3)
        }
    }
}
