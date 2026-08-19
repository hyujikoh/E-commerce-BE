package com.loopers.domain.property

/** 객실 타입 포트. */
interface RoomTypeRepository {
    fun save(roomType: RoomType): RoomType

    /** 숙소의 삭제되지 않은 객실 타입 목록. */
    fun findAllByPropertyId(propertyId: Long): List<RoomType>
}
