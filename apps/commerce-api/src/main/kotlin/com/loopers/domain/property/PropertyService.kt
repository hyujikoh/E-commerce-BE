package com.loopers.domain.property

import com.loopers.domain.common.PageResult
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 숙소 조회 서비스 — 검색(read model)과 상세를 제공한다.
 * 찜 수 병합·캐시 등 조합 관심사는 PropertyFacade 가 담당한다.
 */
@Component
class PropertyService(
    private val propertyRepository: PropertyRepository,
    private val roomTypeRepository: RoomTypeRepository,
    private val propertySearchRepository: PropertySearchRepository,
) {
    @Transactional(readOnly = true)
    fun search(condition: PropertySearchCondition): PageResult<PropertySearchRow> =
        propertySearchRepository.search(condition)

    @Transactional(readOnly = true)
    fun getDetail(propertyId: Long): PropertyDetail {
        val property = propertyRepository.find(propertyId)
            ?: throw CoreException(ErrorType.PROPERTY_NOT_FOUND)
        return PropertyDetail(property, roomTypeRepository.findAllByPropertyId(propertyId))
    }
}
