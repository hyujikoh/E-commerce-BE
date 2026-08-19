package com.loopers.config.monitoring

import org.springframework.boot.actuate.endpoint.annotation.Endpoint
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation
import java.lang.management.ManagementFactory
import java.lang.management.MemoryUsage

/**
 * heap/non-heap 메모리 풀과 GC 상태를 즉석에서 확인하는 커스텀 액추에이터 엔드포인트.
 * URL: {management.server.port}/actuator/jvm
 */
@Endpoint(id = "jvm")
class JvmMemoryEndpoint(
    private val history: GcEventHistory,
) {
    @ReadOperation
    fun report(): JvmMemoryReport {
        val memory = ManagementFactory.getMemoryMXBean()
        val pools = ManagementFactory.getMemoryPoolMXBeans().map { pool ->
            val usage = pool.usage
            MemoryPoolInfo(
                name = pool.name,
                type = pool.type.name,
                init = usage.init,
                used = usage.used,
                committed = usage.committed,
                max = usage.max,
                usedRatio = if (usage.max > 0) usage.used.toDouble() / usage.max else null,
            )
        }
        val gc = ManagementFactory.getGarbageCollectorMXBeans().map { collector ->
            GcCollectorSummary(
                name = collector.name,
                collectionCount = collector.collectionCount,
                collectionTimeMillis = collector.collectionTime,
                memoryPoolNames = collector.memoryPoolNames.toList(),
            )
        }
        return JvmMemoryReport(
            heap = memory.heapMemoryUsage.toInfo(),
            nonHeap = memory.nonHeapMemoryUsage.toInfo(),
            pools = pools,
            gc = gc,
            recentGcEvents = history.snapshot(),
        )
    }

    private fun MemoryUsage.toInfo(): MemoryUsageInfo = MemoryUsageInfo(
        init = init,
        used = used,
        committed = committed,
        max = max,
    )
}

data class JvmMemoryReport(
    val heap: MemoryUsageInfo,
    val nonHeap: MemoryUsageInfo,
    val pools: List<MemoryPoolInfo>,
    val gc: List<GcCollectorSummary>,
    val recentGcEvents: List<GcEvent>,
)

data class MemoryUsageInfo(
    val init: Long,
    val used: Long,
    val committed: Long,
    val max: Long,
)

data class MemoryPoolInfo(
    val name: String,
    val type: String,
    val init: Long,
    val used: Long,
    val committed: Long,
    val max: Long,
    val usedRatio: Double?,
)

data class GcCollectorSummary(
    val name: String,
    val collectionCount: Long,
    val collectionTimeMillis: Long,
    val memoryPoolNames: List<String>,
)
