package com.loopers.domain.property

/**
 * 숙소 검색 결과 read model 1행.
 * minTotalAmount 는 검색 기간 총액(1박 요금의 기간 합) 중 숙소 내 최저가 객실 타입 기준, KRW 정수.
 */
data class PropertySearchRow(
    val propertyId: Long,
    val name: String,
    val city: String,
    val wishlistCount: Long,
    val minTotalAmount: Long,
)
