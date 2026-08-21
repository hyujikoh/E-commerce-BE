package com.loopers.infrastructure.property

import com.loopers.domain.property.RoomType
import com.loopers.domain.property.RoomTypeRepository
import org.springframework.stereotype.Component

@Component
class RoomTypeRepositoryImpl(
    private val roomTypeJpaRepository: RoomTypeJpaRepository,
) : RoomTypeRepository {
    override fun save(roomType: RoomType): RoomType = roomTypeJpaRepository.save(roomType)

    override fun findAllByPropertyId(propertyId: Long): List<RoomType> =
        roomTypeJpaRepository.findAllByPropertyIdAndDeletedAtIsNull(propertyId)
}
