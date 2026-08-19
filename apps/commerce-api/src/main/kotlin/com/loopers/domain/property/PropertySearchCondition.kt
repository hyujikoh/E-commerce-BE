package com.loopers.domain.property

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 숙소 검색 조건. 생성 시점에 유효성을 강제하여 이후 계층(SQL, 캐시 키)이 검증 없이 신뢰할 수 있게 한다.
 */
data class PropertySearchCondition(
    val city: String,
    val checkIn: LocalDate,
    val checkOut: LocalDate,
    val guestCount: Int,
    val sort: PropertySortType,
    val page: Int,
    val size: Int,
) {
    /** 숙박 일수. 가용 판정(요금·재고가 전 일자 존재)의 기준 값. */
    val nights: Long = ChronoUnit.DAYS.between(checkIn, checkOut)

    init {
        if (city.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "도시는 비어 있을 수 없습니다.")
        }
        if (nights < 1) {
            throw CoreException(ErrorType.BAD_REQUEST, "체크아웃은 체크인 이후여야 합니다.")
        }
        if (nights > MAX_NIGHTS) {
            throw CoreException(ErrorType.BAD_REQUEST, "최대 ${MAX_NIGHTS}박까지 검색할 수 있습니다.")
        }
        if (guestCount < 1) {
            throw CoreException(ErrorType.BAD_REQUEST, "인원은 1명 이상이어야 합니다.")
        }
        if (page < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "페이지는 0 이상이어야 합니다.")
        }
        if (size !in 1..MAX_SIZE) {
            throw CoreException(ErrorType.BAD_REQUEST, "페이지 크기는 1~${MAX_SIZE} 사이여야 합니다.")
        }
    }

    companion object {
        const val MAX_NIGHTS = 30L
        const val MAX_SIZE = 100
    }
}
