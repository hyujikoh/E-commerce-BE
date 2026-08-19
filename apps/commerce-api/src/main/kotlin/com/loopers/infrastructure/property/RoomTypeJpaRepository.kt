package com.loopers.infrastructure.property

import com.loopers.domain.property.RoomType
import org.springframework.data.jpa.repository.JpaRepository

interface RoomTypeJpaRepository : JpaRepository<RoomType, Long> {
    fun findAllByPropertyIdAndDeletedAtIsNull(propertyId: Long): List<RoomType>
}
