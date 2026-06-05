package com.loopers.interfaces.api

import com.loopers.domain.accommodation.DailyRoomInventory
import com.loopers.domain.accommodation.DailyRoomRate
import com.loopers.domain.accommodation.ReservationStatus
import com.loopers.domain.accommodation.vo.Money
import com.loopers.infrastructure.accommodation.DailyRoomInventoryJpaRepository
import com.loopers.infrastructure.accommodation.DailyRoomRateJpaRepository
import com.loopers.interfaces.api.accommodation.ReservationV1Dto
import com.loopers.utils.DatabaseCleanUp
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
import org.springframework.http.MediaType
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReservationV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val dailyRoomRateJpaRepository: DailyRoomRateJpaRepository,
    private val dailyRoomInventoryJpaRepository: DailyRoomInventoryJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val roomTypeId = 100L
    private val dates = listOf(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 11))

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api/v1/reservations")
    @Nested
    inner class Create {
        @DisplayName("요금·재고가 충분하면, PENDING 예약을 생성하고 총액을 반환한다.")
        @Test
        fun createsPendingReservation() {
            // arrange
            dates.forEach { dailyRoomRateJpaRepository.save(DailyRoomRate(roomTypeId, it, Money.krw(50_000))) }
            dates.forEach { dailyRoomInventoryJpaRepository.save(DailyRoomInventory(roomTypeId, it, remaining = 1)) }

            val body = """{"guestId":1,"roomTypeId":$roomTypeId,"checkIn":"2026-06-10","checkOut":"2026-06-12"}"""
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<ReservationV1Dto.ReservationResponse>>() {}
            val response = testRestTemplate.exchange("/api/v1/reservations", HttpMethod.POST, HttpEntity(body, headers), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.status).isEqualTo(ReservationStatus.PENDING) },
                { assertThat(response.body?.data?.totalAmount).isEqualTo(100_000) },
                { assertThat(response.body?.data?.nightly).hasSize(2) },
            )
        }

        @DisplayName("재고가 없으면, 409 CONFLICT 응답을 받는다.")
        @Test
        fun returnsConflict_whenNoInventory() {
            // arrange — 요금만 있고 재고 없음
            dates.forEach { dailyRoomRateJpaRepository.save(DailyRoomRate(roomTypeId, it, Money.krw(50_000))) }

            val body = """{"guestId":1,"roomTypeId":$roomTypeId,"checkIn":"2026-06-10","checkOut":"2026-06-12"}"""
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<ReservationV1Dto.ReservationResponse>>() {}
            val response = testRestTemplate.exchange("/api/v1/reservations", HttpMethod.POST, HttpEntity(body, headers), responseType)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        }
    }
}
