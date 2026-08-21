package com.loopers.domain.property

import com.loopers.domain.common.PageResult

/**
 * 숙소 검색 포트. 조건(도시·일자 범위·인원)에 맞는 가용 숙소를 정렬 기준에 따라 페이지로 반환한다.
 *
 * "가용"의 정의: 검색 기간의 모든 일자에 요금이 존재하고 잔여 재고가 1 이상인 객실 타입이
 * 하나 이상 있는 숙소.
 */
interface PropertySearchRepository {
    fun search(condition: PropertySearchCondition): PageResult<PropertySearchRow>
}
