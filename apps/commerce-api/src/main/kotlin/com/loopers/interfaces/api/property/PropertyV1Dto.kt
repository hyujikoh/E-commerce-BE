package com.loopers.interfaces.api.property

import com.loopers.application.property.PropertyInfo

class PropertyV1Dto {
    data class SearchPageResponse(
        val items: List<SearchItemResponse>,
        val page: Int,
        val size: Int,
        val totalElements: Long,
        val totalPages: Int,
    ) {
        companion object {
            fun from(info: PropertyInfo.SearchPage): SearchPageResponse = SearchPageResponse(
                items = info.items.map {
                    SearchItemResponse(it.propertyId, it.name, it.city, it.wishlistCount, it.minTotalAmount)
                },
                page = info.page,
                size = info.size,
                totalElements = info.totalElements,
                totalPages = info.totalPages,
            )
        }
    }

    data class SearchItemResponse(
        val propertyId: Long,
        val name: String,
        val city: String,
        val wishlistCount: Long,
        /** 검색 기간 총액(숙소 내 최저가 객실 기준), KRW */
        val minTotalAmount: Long,
    )

    data class DetailResponse(
        val id: Long,
        val name: String,
        val city: String,
        val wishlistCount: Long,
        val roomTypes: List<RoomTypeResponse>,
    ) {
        companion object {
            fun from(info: PropertyInfo.Detail): DetailResponse = DetailResponse(
                id = info.id,
                name = info.name,
                city = info.city,
                wishlistCount = info.wishlistCount,
                roomTypes = info.roomTypes.map { RoomTypeResponse(it.id, it.name, it.capacity) },
            )
        }
    }

    data class RoomTypeResponse(
        val id: Long,
        val name: String,
        val capacity: Int,
    )
}
