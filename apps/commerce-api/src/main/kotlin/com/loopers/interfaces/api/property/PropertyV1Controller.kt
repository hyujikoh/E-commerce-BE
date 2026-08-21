package com.loopers.interfaces.api.property

import com.loopers.application.property.PropertyFacade
import com.loopers.domain.property.PropertySearchCondition
import com.loopers.domain.property.PropertySortType
import com.loopers.interfaces.api.ApiResponse
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/properties")
class PropertyV1Controller(
    private val propertyFacade: PropertyFacade,
) : PropertyV1ApiSpec {
    @GetMapping
    override fun search(
        @RequestParam city: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) checkIn: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) checkOut: LocalDate,
        @RequestParam guestCount: Int,
        @RequestParam(defaultValue = "PRICE_ASC") sort: PropertySortType,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<PropertyV1Dto.SearchPageResponse> {
        val condition = PropertySearchCondition(city, checkIn, checkOut, guestCount, sort, page, size)
        return ApiResponse.success(PropertyV1Dto.SearchPageResponse.from(propertyFacade.search(condition)))
    }

    @GetMapping("/{propertyId}")
    override fun getDetail(
        @PathVariable propertyId: Long,
    ): ApiResponse<PropertyV1Dto.DetailResponse> {
        return ApiResponse.success(PropertyV1Dto.DetailResponse.from(propertyFacade.getDetail(propertyId)))
    }
}
