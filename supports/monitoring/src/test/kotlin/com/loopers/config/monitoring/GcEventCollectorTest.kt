package com.loopers.config.monitoring

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.lang.management.ManagementFactory
import java.lang.management.MemoryType
import java.time.Instant

class GcEventCollectorTest {
    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var history: GcEventHistory
    private lateinit var collector: GcEventCollector

    // record()는 실제 JVM의 heap 풀 이름으로 필터링하므로 현재 JVM의 heap 풀을 사용한다
    private val heapPoolName = ManagementFactory.getMemoryPoolMXBeans()
        .first { it.type == MemoryType.HEAP }
        .name

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        history = GcEventHistory()
        collector = GcEventCollector(meterRegistry, history)
    }

    private fun gcEvent(
        cause: String = "G1 Evacuation Pause",
        reclaimedBytes: Long = 4_096,
        poolChanges: List<PoolChange> = listOf(PoolChange(heapPoolName, 10_000, 6_000)),
    ): GcEvent = GcEvent(
        id = 1,
        gcName = "G1 Young Generation",
        action = "end of minor GC",
        cause = cause,
        timestamp = Instant.now(),
        durationMillis = 5,
        reclaimedBytes = reclaimedBytes,
        poolChanges = poolChanges,
    )

    @DisplayName("record는")
    @Nested
    inner class Record {
        @DisplayName("jvm.gc.reclaimed.bytes summary에 gc/cause 태그로 회수량을 기록한다")
        @Test
        fun recordsReclaimedBytesSummary() {
            collector.record(gcEvent(reclaimedBytes = 4_096))
            collector.record(gcEvent(reclaimedBytes = 1_000))

            val summary = meterRegistry.get("jvm.gc.reclaimed.bytes")
                .tags("gc", "G1 Young Generation", "cause", "G1 Evacuation Pause")
                .summary()
            assertThat(summary.count()).isEqualTo(2)
            assertThat(summary.totalAmount()).isEqualTo(5_096.0)
        }

        @DisplayName("heap 풀 변화가 있으면 jvm.gc.pool.used.after 게이지를 갱신한다")
        @Test
        fun updatesPoolUsedAfterGauge() {
            collector.record(gcEvent(poolChanges = listOf(PoolChange(heapPoolName, 10_000, 6_000))))
            collector.record(gcEvent(poolChanges = listOf(PoolChange(heapPoolName, 12_000, 7_500))))

            val gauge = meterRegistry.get("jvm.gc.pool.used.after")
                .tag("pool", heapPoolName)
                .gauge()
            assertThat(gauge.value()).isEqualTo(7_500.0)
        }

        @DisplayName("non-heap 풀 변화는 게이지 대상에서 제외한다")
        @Test
        fun ignoresNonHeapPoolChanges() {
            collector.record(gcEvent(poolChanges = listOf(PoolChange("Metaspace", 10_000, 11_000))))

            assertThat(meterRegistry.find("jvm.gc.pool.used.after").gauges()).isEmpty()
        }

        @DisplayName("이벤트를 히스토리에 적재한다")
        @Test
        fun appendsToHistory() {
            val event = gcEvent()

            collector.record(event)

            assertThat(history.snapshot()).containsExactly(event)
        }
    }

    @DisplayName("Metaspace 경고는")
    @Nested
    inner class MetaspaceWarn {
        private lateinit var appender: ListAppender<ILoggingEvent>
        private val logger = LoggerFactory.getLogger(GcEventCollector::class.java) as Logger

        @BeforeEach
        fun attachAppender() {
            appender = ListAppender<ILoggingEvent>().apply { start() }
            logger.addAppender(appender)
        }

        @AfterEach
        fun detachAppender() {
            logger.detachAppender(appender)
        }

        @DisplayName("GC cause가 Metadata GC Threshold이면 warn 로그를 남긴다")
        @Test
        fun warnsOnMetadataGcThresholdCause() {
            collector.record(gcEvent(cause = "Metadata GC Threshold"))

            assertThat(appender.list)
                .anyMatch { it.level == Level.WARN && it.formattedMessage.contains("Metaspace") }
        }

        @DisplayName("일반 cause에는 warn 로그를 남기지 않는다")
        @Test
        fun doesNotWarnOnNormalCause() {
            collector.record(gcEvent(cause = "G1 Evacuation Pause"))

            // MaxMetaspaceSize 미설정(max = -1) 환경이므로 사용률 경고도 발생하지 않는다
            assertThat(appender.list).noneMatch { it.level == Level.WARN }
        }
    }
}
