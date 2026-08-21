package com.loopers.infrastructure.property

import com.loopers.domain.property.Property
import com.loopers.domain.property.PropertyRepository
import org.springframework.stereotype.Component

@Component
class PropertyRepositoryImpl(
    private val propertyJpaRepository: PropertyJpaRepository,
) : PropertyRepository {
    override fun save(property: Property): Property = propertyJpaRepository.save(property)

    override fun find(id: Long): Property? = propertyJpaRepository.findByIdAndDeletedAtIsNull(id)
}
