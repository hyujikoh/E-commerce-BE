package com.loopers.domain.property

/** 숙소 포트. */
interface PropertyRepository {
    fun save(property: Property): Property

    /** 삭제되지 않은 숙소 조회. 없으면 null. */
    fun find(id: Long): Property?
}
