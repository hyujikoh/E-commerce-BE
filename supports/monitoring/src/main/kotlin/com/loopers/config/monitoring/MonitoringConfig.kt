package com.loopers.config.monitoring

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MonitoringConfig {
    @Bean
    fun gcEventHistory(): GcEventHistory = GcEventHistory()

    @Bean
    fun gcEventCollector(meterRegistry: MeterRegistry, gcEventHistory: GcEventHistory): GcEventCollector =
        GcEventCollector(meterRegistry, gcEventHistory)

    @Bean
    fun jvmMemoryEndpoint(gcEventHistory: GcEventHistory): JvmMemoryEndpoint = JvmMemoryEndpoint(gcEventHistory)
}
