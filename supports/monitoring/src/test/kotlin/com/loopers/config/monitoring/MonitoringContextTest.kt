package com.loopers.config.monitoring

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Duration

@SpringBootTest
class MonitoringContextTest @Autowired constructor(
    private val gcEventHistory: GcEventHistory,
    private val jvmMemoryEndpoint: JvmMemoryEndpoint,
) {
    @DisplayName("System.gc()를 호출하면 notification 리스너가 GC 이벤트를 히스토리에 수집한다")
    @Test
    fun collectsGcEvent_whenGcTriggered() {
        System.gc()

        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            assertThat(gcEventHistory.snapshot()).anyMatch { it.cause == "System.gc()" }
        }
    }

    @DisplayName("커스텀 엔드포인트 빈이 등록되고 report가 정상 응답한다")
    @Test
    fun jvmEndpointBeanIsRegistered() {
        val report = jvmMemoryEndpoint.report()

        assertThat(report.pools).isNotEmpty()
        assertThat(report.gc).isNotEmpty()
    }
}
