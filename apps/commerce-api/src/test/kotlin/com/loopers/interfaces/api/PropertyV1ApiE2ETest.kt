package com.loopers.interfaces.api

import com.loopers.domain.accommodation.DailyRoomInventory
import com.loopers.domain.accommodation.DailyRoomRate
import com.loopers.domain.accommodation.vo.Money
import com.loopers.domain.property.Property
import com.loopers.domain.property.PropertyRepository
import com.loopers.domain.property.RoomType
import com.loopers.domain.property.RoomTypeRepository
import com.loopers.infrastructure.accommodation.DailyRoomInventoryJpaRepository
import com.loopers.infrastructure.accommodation.DailyRoomRateJpaRepository
import com.loopers.interfaces.api.property.PropertyV1Dto
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PropertyV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val propertyRepository: PropertyRepository,
    private val roomTypeRepository: RoomTypeRepository,
    private val dailyRoomInventoryJpaRepository: DailyRoomInventoryJpaRepository,
    private val dailyRoomRateJpaRepository: DailyRoomRateJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    private val checkIn = LocalDate.of(2026, 5, 15)
    private val checkOut = LocalDate.of(2026, 5, 17)

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    private val searchType = object : ParameterizedTypeReference<ApiResponse<PropertyV1Dto.SearchPageResponse>>() {}
    private val detailType = object : ParameterizedTypeReference<ApiResponse<PropertyV1Dto.DetailResponse>>() {}

    private fun seedAvailableProperty(name: String = "테스트숙소", city: String = "서울"): Property {
        val property = propertyRepository.save(Property(name, city))
        val roomType = roomTypeRepository.save(RoomType(property.id, "스탠다드", 2))
        var date = checkIn
        while (date < checkOut) {
            dailyRoomInventoryJpaRepository.save(DailyRoomInventory(roomType.id, date, 3))
            dailyRoomRateJpaRepository.save(DailyRoomRate(roomType.id, date, Money(50_000L, "KRW")))
            date = date.plusDays(1)
        }
        return property
    }

    // 한글 도시명이 이중 인코딩되지 않도록 URI 객체로 전달한다(문자열은 URI 템플릿으로 재인코딩됨).
    private fun searchUrl(city: String = "서울", checkIn: LocalDate = this.checkIn, checkOut: LocalDate = this.checkOut): URI =
        UriComponentsBuilder.fromPath("/api/v1/properties")
            .queryParam("city", city)
            .queryParam("checkIn", checkIn)
            .queryParam("checkOut", checkOut)
            .queryParam("guestCount", 2)
            .build()
            .encode()
            .toUri()

    @DisplayName("GET /api/v1/properties")
    @Nested
    inner class Search {
        @DisplayName("조건에 맞는 가용 숙소 목록을 반환한다.")
        @Test
        fun returnsSearchPage() {
            val property = seedAvailableProperty()

            val response = testRestTemplate.exchange(searchUrl(), HttpMethod.GET, HttpEntity(null, HttpHeaders()), searchType)

            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.items?.map { it.propertyId }).containsExactly(property.id) },
                { assertThat(response.body?.data?.items?.first()?.minTotalAmount).isEqualTo(100_000L) },
            )
        }

        @DisplayName("체크아웃이 체크인 이전이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenInvalidDateRange() {
            val url = searchUrl(checkIn = checkOut, checkOut = checkIn)

            val response = testRestTemplate.exchange(url, HttpMethod.GET, HttpEntity(null, HttpHeaders()), searchType)

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    @DisplayName("GET /api/v1/properties/{propertyId}")
    @Nested
    inner class Detail {
        @DisplayName("숙소 상세와 객실 타입, 찜 수를 반환한다.")
        @Test
        fun returnsDetail() {
            val property = seedAvailableProperty(name = "상세숙소")

            val response = testRestTemplate.exchange(
                "/api/v1/properties/${property.id}",
                HttpMethod.GET,
                HttpEntity(null, HttpHeaders()),
                detailType,
            )

            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.name).isEqualTo("상세숙소") },
                { assertThat(response.body?.data?.roomTypes).hasSize(1) },
                { assertThat(response.body?.data?.wishlistCount).isEqualTo(0L) },
            )
        }

        @DisplayName("존재하지 않는 숙소면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenAbsent() {
            val response = testRestTemplate.exchange(
                "/api/v1/properties/999",
                HttpMethod.GET,
                HttpEntity(null, HttpHeaders()),
                detailType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }
}
