package com.loopers.interfaces.api

import com.loopers.domain.accommodation.DailyRoomInventory
import com.loopers.domain.accommodation.DailyRoomRate
import com.loopers.domain.accommodation.ReservationStatus
import com.loopers.domain.accommodation.vo.Money
import com.loopers.infrastructure.accommodation.DailyRoomInventoryJpaRepository
import com.loopers.infrastructure.accommodation.DailyRoomRateJpaRepository
import com.loopers.interfaces.api.accommodation.ReservationV1Dto
import com.loopers.interfaces.api.coupon.CouponAdminV1Dto
import com.loopers.interfaces.api.coupon.CouponV1Dto
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

        @DisplayName("쿠폰을 적용하면, 할인 전/할인/최종 금액을 모두 스냅샷에 담아 예약을 생성하고 쿠폰을 USED 로 만든다.")
        @Test
        fun createsReservationWithCoupon() {
            // arrange — 요금 100,000 / 재고 충분
            dates.forEach { dailyRoomRateJpaRepository.save(DailyRoomRate(roomTypeId, it, Money.krw(50_000))) }
            dates.forEach { dailyRoomInventoryJpaRepository.save(DailyRoomInventory(roomTypeId, it, remaining = 1)) }

            // 관리자: 정액 1만원 쿠폰 템플릿 등록
            val jsonHeaders = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
            val couponBody = """{"name":"1만원 할인","type":"FIXED","value":10000,"expiredAt":"2026-12-31T23:59:59"}"""
            val couponType = object : ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponResponse>>() {}
            val templateId = testRestTemplate
                .exchange("/api-admin/v1/coupons", HttpMethod.POST, HttpEntity(couponBody, jsonHeaders), couponType)
                .body!!.data!!.id

            // 사용자(guestId=1): 쿠폰 발급
            val userHeaders = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
                set("X-USER-ID", "1")
            }
            val issueType = object : ParameterizedTypeReference<ApiResponse<CouponV1Dto.IssuedCouponResponse>>() {}
            val issuedCouponId = testRestTemplate
                .exchange("/api/v1/coupons/$templateId/issue", HttpMethod.POST, HttpEntity(null, userHeaders), issueType)
                .body!!.data!!.issuedCouponId

            val body =
                """{"guestId":1,"roomTypeId":$roomTypeId,"checkIn":"2026-06-10","checkOut":"2026-06-12","couponId":$issuedCouponId}"""

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<ReservationV1Dto.ReservationResponse>>() {}
            val response = testRestTemplate.exchange("/api/v1/reservations", HttpMethod.POST, HttpEntity(body, jsonHeaders), responseType)

            // assert: 예약 스냅샷 금액 + 쿠폰 USED 전이
            val ownedType = object : ParameterizedTypeReference<ApiResponse<List<CouponV1Dto.OwnedCouponResponse>>>() {}
            val ownedAfter = testRestTemplate
                .exchange("/api/v1/users/me/coupons", HttpMethod.GET, HttpEntity(null, userHeaders), ownedType)
                .body!!.data!!.first { it.issuedCouponId == issuedCouponId }

            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.status).isEqualTo(ReservationStatus.PENDING) },
                { assertThat(response.body?.data?.totalAmount).isEqualTo(100_000) },
                { assertThat(response.body?.data?.discountAmount).isEqualTo(10_000) },
                { assertThat(response.body?.data?.finalAmount).isEqualTo(90_000) },
                { assertThat(response.body?.data?.appliedCouponId).isEqualTo(issuedCouponId) },
                { assertThat(ownedAfter.status).isEqualTo(com.loopers.domain.coupon.IssuedCouponStatus.USED) },
            )
        }

        @DisplayName("이미 사용된 쿠폰으로 예약하면, 409 CONFLICT 응답을 받고 재고는 추가로 차감되지 않는다.")
        @Test
        fun returnsConflict_whenCouponAlreadyUsed() {
            // arrange — 요금, 재고 2씩(1차 예약 후에도 1씩 남도록)
            dates.forEach { dailyRoomRateJpaRepository.save(DailyRoomRate(roomTypeId, it, Money.krw(50_000))) }
            dates.forEach { dailyRoomInventoryJpaRepository.save(DailyRoomInventory(roomTypeId, it, remaining = 2)) }

            val jsonHeaders = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
            val couponBody = """{"name":"1만원 할인","type":"FIXED","value":10000,"expiredAt":"2026-12-31T23:59:59"}"""
            val couponType = object : ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponResponse>>() {}
            val templateId = testRestTemplate
                .exchange("/api-admin/v1/coupons", HttpMethod.POST, HttpEntity(couponBody, jsonHeaders), couponType)
                .body!!.data!!.id

            val userHeaders = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
                set("X-USER-ID", "1")
            }
            val issueType = object : ParameterizedTypeReference<ApiResponse<CouponV1Dto.IssuedCouponResponse>>() {}
            val issuedCouponId = testRestTemplate
                .exchange("/api/v1/coupons/$templateId/issue", HttpMethod.POST, HttpEntity(null, userHeaders), issueType)
                .body!!.data!!.issuedCouponId

            // 1차 예약으로 쿠폰을 USED 로 소진 (재고 각 2 → 1)
            val firstBody =
                """{"guestId":1,"roomTypeId":$roomTypeId,"checkIn":"2026-06-10","checkOut":"2026-06-12","couponId":$issuedCouponId}"""
            val responseType = object : ParameterizedTypeReference<ApiResponse<ReservationV1Dto.ReservationResponse>>() {}
            testRestTemplate.exchange("/api/v1/reservations", HttpMethod.POST, HttpEntity(firstBody, jsonHeaders), responseType)

            // act — 같은 쿠폰으로 2차 예약 (쿠폰 검증이 재고 차감보다 먼저이므로 재고는 손대지 않음)
            val response = testRestTemplate.exchange("/api/v1/reservations", HttpMethod.POST, HttpEntity(firstBody, jsonHeaders), responseType)

            // assert — 쿠폰 사유 충돌 + 재고는 1차 차감분(각 1씩, 합 2)만 반영되고 추가 차감 없음
            val remaining = dailyRoomInventoryJpaRepository.findAll().filter { it.roomTypeId == roomTypeId }.sumOf { it.remaining }
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
                { assertThat(remaining).isEqualTo(2) },
            )
        }
    }
}
