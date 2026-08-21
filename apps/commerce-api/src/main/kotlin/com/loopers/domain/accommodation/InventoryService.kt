package com.loopers.domain.accommodation

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * 일자별 재고 차감/복구의 단독 책임자(B1). 동시성 제어를 이 한 곳에 응집한다.
 *
 * [reserve] 는 원자적 UPDATE(`... WHERE remaining > 0`)로 일자별 재고를 1씩 차감한다.
 * 같은 객실의 마지막 1개를 두 요청이 동시에 노려도 DB 차원에서 한 요청만 성공한다(더블부킹 방지).
 * 한 일자라도 차감에 실패하면 예외를 던져 트랜잭션을 롤백시킨다 — 같은 트랜잭션 안에서 이미 차감된 일자도
 * 함께 롤백되므로 별도 보상 로직이 필요 없다(B5: 단일 RDB 트랜잭션).
 *
 * [release] 는 예약 취소/만료 시 점유했던 재고를 되돌린다. 호출 자격(취소/만료가 유일하게 성공한 전이인지)은
 * Reservation 상태 전이 가드가 보장하므로, 여기서는 일자별 +1 만 수행한다.
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

    @Transactional
    fun release(roomTypeId: Long, dates: List<LocalDate>) {
        for (date in dates) {
            val incremented = inventoryRepository.increment(roomTypeId, date)
            if (!incremented) {
                // reserve 가 성공했던 (roomTypeId, date) 행이 사라진 경우 — 데이터 손상이므로 조용히 넘기지 않는다.
                throw CoreException(ErrorType.INTERNAL_ERROR, "[$date] 재고 행이 없어 복구할 수 없습니다.")
            }
        }
    }
}
