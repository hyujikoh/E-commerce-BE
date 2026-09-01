package com.loopers.application.payment.scheduler

import com.loopers.application.payment.PaymentFacade
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 결제 상태 동기화 스케줄러. 30초 주기로 결과 미확정(CREATED/REQUESTED) 결제를 PG 조회로 사후 확정한다 —
 * 콜백 유실 건과 접수 응답 유실(타임아웃) 건의 복구 경로다. 실제 처리는 PaymentFacade.syncPendingResults 가 담당한다.
 *
 * `scheduler.payment-sync.enabled=false` 로 끌 수 있다(기본 on).
 * 통합 테스트는 백그라운드 실행이 시드 데이터를 건드리지 않도록 이 스위치로 끈다.
 */
@Component
@ConditionalOnProperty(
    name = ["scheduler.payment-sync.enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class PaymentSyncScheduler(
    private val paymentFacade: PaymentFacade,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 30_000)
    fun syncPendingResults() {
        val synced = paymentFacade.syncPendingResults()
        if (synced > 0) {
            log.info("결과 미확정 결제 {}건의 상태 동기화를 시도했습니다.", synced)
        }
    }
}
