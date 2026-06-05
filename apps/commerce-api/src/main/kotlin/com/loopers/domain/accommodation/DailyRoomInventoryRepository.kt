package com.loopers.domain.accommodation

import java.time.LocalDate

/**
 * 일자별 재고 포트. 차감은 동시성 안전을 위해 원자적 UPDATE 로 구현된다.
 * (복구 increment 는 취소/만료 슬라이스에서 추가한다.)
 */
interface DailyRoomInventoryRepository {
    /**
     * (roomTypeId, date)의 재고를 1 차감한다. 잔여가 1 이상일 때만 성공한다.
     * @return 차감 성공 여부. 재고가 없거나 행이 없으면 false.
     */
    fun decrementIfAvailable(roomTypeId: Long, date: LocalDate): Boolean
}
