package com.loopers.interfaces.api.property

import com.loopers.domain.property.PropertySortType
import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import java.time.LocalDate

@Tag(name = "Property V1 API", description = "숙소 검색/상세 API")
interface PropertyV1ApiSpec {
    @Operation(
        summary = "숙소 검색",
        description = "도시·체크인/체크아웃·인원 조건에 맞는 가용 숙소를 정렬 기준(가격순/찜순/추천순)에 따라 페이지로 반환한다.",
    )
    fun search(
        city: String,
        checkIn: LocalDate,
        checkOut: LocalDate,
        guestCount: Int,
        sort: PropertySortType,
        page: Int,
        size: Int,
    ): ApiResponse<PropertyV1Dto.SearchPageResponse>

    @Operation(summary = "숙소 상세 조회", description = "숙소 기본 정보, 객실 타입 목록, 찜 수를 반환한다.")
    fun getDetail(propertyId: Long): ApiResponse<PropertyV1Dto.DetailResponse>
}
