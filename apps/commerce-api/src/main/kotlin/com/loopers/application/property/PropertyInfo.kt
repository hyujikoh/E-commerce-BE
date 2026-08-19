package com.loopers.application.property

import com.loopers.domain.common.PageResult
import com.loopers.domain.property.PropertyDetail
import com.loopers.domain.property.PropertySearchRow

class PropertyInfo {
    /**
     * 상세 캐시 대상 — 찜 수를 제외한 기본 정보.
     * 찜 수는 변동이 잦아 캐시하지 않고 조회 시점에 병합한다(PropertyFacade.getDetail 참조).
     */
    data class DetailBase(
        val id: Long,
        val name: String,
        val city: String,
        val roomTypes: List<RoomTypeItem>,
    ) {
        companion object {
            fun from(detail: PropertyDetail): DetailBase = DetailBase(
                id = detail.property.id,
                name = detail.property.name,
                city = detail.property.city,
                roomTypes = detail.roomTypes.map { RoomTypeItem(it.id, it.name, it.capacity) },
            )
        }
    }

    data class RoomTypeItem(
        val id: Long,
        val name: String,
        val capacity: Int,
    )

    data class Detail(
        val id: Long,
        val name: String,
        val city: String,
        val wishlistCount: Long,
        val roomTypes: List<RoomTypeItem>,
    ) {
        companion object {
            fun of(base: DetailBase, wishlistCount: Long): Detail =
                Detail(base.id, base.name, base.city, wishlistCount, base.roomTypes)
        }
    }

    data class SearchPage(
        val items: List<SearchItem>,
        val page: Int,
        val size: Int,
        val totalElements: Long,
        val totalPages: Int,
    ) {
        companion object {
            fun from(result: PageResult<PropertySearchRow>): SearchPage = SearchPage(
                items = result.content.map {
                    SearchItem(it.propertyId, it.name, it.city, it.wishlistCount, it.minTotalAmount)
                },
                page = result.page,
                size = result.size,
                totalElements = result.totalElements,
                totalPages = result.totalPages,
            )
        }
    }

    data class SearchItem(
        val propertyId: Long,
        val name: String,
        val city: String,
        val wishlistCount: Long,
        val minTotalAmount: Long,
    )
}
