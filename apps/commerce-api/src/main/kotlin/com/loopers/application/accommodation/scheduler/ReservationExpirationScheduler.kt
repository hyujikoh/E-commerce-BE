package com.loopers.application.accommodation.scheduler

import com.loopers.domain.accommodation.ReservationService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 소프트 홀드 만료 스케줄러(P4). 1분 주기로 만료 시각이 지난 PENDING 예약을 취소 정리한다.
 * 실제 처리(FOR UPDATE SKIP LOCKED 스캔 + 취소 + 재고·쿠폰 복구)는 ReservationService.expireOverdue 가 담당한다.
 *
 * `scheduler.reservation-expiration.enabled=false` 로 끌 수 있다(기본 on).
 * 통합 테스트는 백그라운드 실행이 시드 데이터를 건드리지 않도록 이 스위치로 끈다.
 */
@Component
@ConditionalOnProperty(
    name = ["scheduler.reservation-expiration.enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class ReservationExpirationScheduler(
    private val reservationService: ReservationService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 60_000)
    fun expireOverduePendings() {
        val expired = reservationService.expireOverdue()
        if (expired > 0) {
            log.info("만료된 PENDING 예약 {}건을 취소 처리했습니다.", expired)
        }
    }
}
