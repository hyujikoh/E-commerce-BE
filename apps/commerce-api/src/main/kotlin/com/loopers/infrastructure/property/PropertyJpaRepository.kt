package com.loopers.infrastructure.property

import com.loopers.domain.property.Property
import org.springframework.data.jpa.repository.JpaRepository

interface PropertyJpaRepository : JpaRepository<Property, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): Property?
}
