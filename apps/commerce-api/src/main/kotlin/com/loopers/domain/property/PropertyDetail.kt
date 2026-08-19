package com.loopers.domain.property

/** 숙소 상세 — 숙소와 소속 객실 타입 목록. */
data class PropertyDetail(
    val property: Property,
    val roomTypes: List<RoomType>,
)
