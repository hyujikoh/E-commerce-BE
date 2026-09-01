package com.loopers.config.monitoring

import com.sun.management.GarbageCollectionNotificationInfo
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import java.lang.management.ManagementFactory
import java.lang.management.MemoryPoolMXBean
import java.lang.management.MemoryType
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.management.Notification
import javax.management.NotificationEmitter
import javax.management.NotificationListener
import javax.management.openmbean.CompositeData

/**
 * GarbageCollectorMXBean의 GC notification을 구독하여
 * 기본 JvmGcMetrics가 제공하지 않는 정보를 수집한다:
 * - jvm.gc.reclaimed.bytes: GC 1회당 heap 회수량 (gc/cause 태그)
 * - jvm.gc.pool.used.after: 마지막 GC 직후 heap 풀별 사용량 (live set 근사)
 *
 * pause 시간은 기본 jvm.gc.pause 메트릭이 이미 gc/action/cause 태그로 분해하므로 다루지 않는다.
 * notification 파싱(handle)과 기록(record)을 분리한 이유:
 * GarbageCollectionNotificationInfo는 public 생성자가 없어 테스트에서 직접 만들 수 없다.
 */
class GcEventCollector(
    private val meterRegistry: MeterRegistry,
    private val history: GcEventHistory,
) {
    companion object {
        private const val METASPACE_POOL_NAME = "Metaspace"
        private const val METASPACE_WARN_RATIO = 0.9
        private const val METADATA_GC_CAUSE = "Metadata GC Threshold"
    }

    private val log = LoggerFactory.getLogger(GcEventCollector::class.java)

    private val jvmStartTime = ManagementFactory.getRuntimeMXBean().startTime
    private val heapPoolNames: Set<String> = ManagementFactory.getMemoryPoolMXBeans()
        .filter { it.type == MemoryType.HEAP }
        .map { it.name }
        .toSet()
    private val metaspacePool: MemoryPoolMXBean? = ManagementFactory.getMemoryPoolMXBeans()
        .firstOrNull { it.name == METASPACE_POOL_NAME }

    private val registrations = mutableListOf<Pair<NotificationEmitter, NotificationListener>>()
    private val poolUsedAfterGc = ConcurrentHashMap<String, AtomicLong>()

    @PostConstruct
    fun start() {
        ManagementFactory.getGarbageCollectorMXBeans()
            .filterIsInstance<NotificationEmitter>()
            .forEach { emitter ->
                val listener = NotificationListener { notification, _ -> handle(notification) }
                emitter.addNotificationListener(listener, null, null)
                registrations += emitter to listener
            }
        log.info("GC notification listener {}건 등록", registrations.size)
    }

    @PreDestroy
    fun stop() {
        registrations.forEach { (emitter, listener) ->
            runCatching { emitter.removeNotificationListener(listener) }
        }
        registrations.clear()
    }

    private fun handle(notification: Notification) {
        if (notification.type != GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION) {
            return
        }
        val info = GarbageCollectionNotificationInfo.from(notification.userData as CompositeData)
        record(toEvent(info))
    }

    private fun toEvent(info: GarbageCollectionNotificationInfo): GcEvent {
        val gcInfo = info.gcInfo
        val before = gcInfo.memoryUsageBeforeGc
        val after = gcInfo.memoryUsageAfterGc

        val poolChanges = before.mapNotNull { (pool, beforeUsage) ->
            val afterUsage = after[pool] ?: return@mapNotNull null
            if (beforeUsage.used == afterUsage.used) {
                null
            } else {
                PoolChange(pool, beforeUsage.used, afterUsage.used)
            }
        }

        val reclaimedBytes = poolChanges
            .filter { it.pool in heapPoolNames }
            .sumOf { it.beforeUsedBytes - it.afterUsedBytes }
            .coerceAtLeast(0)

        return GcEvent(
            id = gcInfo.id,
            gcName = info.gcName,
            action = info.gcAction,
            cause = info.gcCause,
            timestamp = Instant.ofEpochMilli(jvmStartTime + gcInfo.endTime),
            durationMillis = gcInfo.duration,
            reclaimedBytes = reclaimedBytes,
            poolChanges = poolChanges,
        )
    }

    internal fun record(event: GcEvent) {
        DistributionSummary.builder("jvm.gc.reclaimed.bytes")
            .baseUnit("bytes")
            .description("GC 1회로 heap에서 회수된 바이트")
            .tag("gc", event.gcName)
            .tag("cause", event.cause)
            .register(meterRegistry)
            .record(event.reclaimedBytes.toDouble())

        event.poolChanges
            .filter { it.pool in heapPoolNames }
            .forEach { change ->
                poolUsedAfterGc.computeIfAbsent(change.pool) { pool ->
                    AtomicLong().also { value ->
                        Gauge.builder("jvm.gc.pool.used.after", value) { it.get().toDouble() }
                            .baseUnit("bytes")
                            .description("마지막 GC 직후 heap 풀별 사용량 (live set 근사)")
                            .tag("pool", pool)
                            .register(meterRegistry)
                    }
                }.set(change.afterUsedBytes)
            }

        warnOnMetaspacePressure(event)
        history.add(event)
    }

    private fun warnOnMetaspacePressure(event: GcEvent) {
        if (event.cause == METADATA_GC_CAUSE) {
            log.warn("Metaspace 부족이 GC를 유발했습니다: gc={}, durationMillis={}", event.gcName, event.durationMillis)
        }
        val usage = metaspacePool?.usage ?: return
        // MaxMetaspaceSize 미설정 시 max = -1이므로 체크를 건너뛴다
        if (usage.max > 0 && usage.used >= usage.max * METASPACE_WARN_RATIO) {
            log.warn("Metaspace 사용률 90% 초과: used={}, max={}", usage.used, usage.max)
        }
    }
}
