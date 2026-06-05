package com.loopers.domain.accommodation

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * 일자별 재고 차감의 단독 책임자(B1). 동시성 제어를 이 한 곳에 응집한다.
 * (재고 복구 release 는 예약 취소/만료 슬라이스에서 호출처와 함께 추가한다.)
 *
 * [reserve] 는 원자적 UPDATE(`... WHERE remaining > 0`)로 일자별 재고를 1씩 차감한다.
 * 같은 객실의 마지막 1개를 두 요청이 동시에 노려도 DB 차원에서 한 요청만 성공한다(더블부킹 방지).
 * 한 일자라도 차감에 실패하면 예외를 던져 트랜잭션을 롤백시킨다 — 같은 트랜잭션 안에서 이미 차감된 일자도
 * 함께 롤백되므로 별도 보상 로직이 필요 없다(B5: 단일 RDB 트랜잭션).
 */
@Component
class InventoryService(
    private val inventoryRepository: DailyRoomInventoryRepository,
) {
    @Transactional
    fun reserve(roomTypeId: Long, dates: List<LocalDate>) {
        for (date in dates) {
            val decremented = inventoryRepository.decrementIfAvailable(roomTypeId, date)
            if (!decremented) {
                throw CoreException(ErrorType.OUT_OF_INVENTORY, "[$date] 재고가 부족합니다.")
            }
        }
    }
}
