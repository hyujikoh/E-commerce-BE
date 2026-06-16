package com.loopers.interfaces.api

import com.loopers.domain.coupon.IssuedCouponStatus
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val userId = 1L
    private val jsonHeaders = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun userHeaders(userId: Long): HttpHeaders =
        HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-USER-ID", userId.toString())
        }

    private fun createTemplate(): Long {
        val body = """{"name":"5천원 할인","type":"FIXED","value":5000,"expiredAt":"2026-12-31T23:59:59"}"""
        val responseType = object : ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponResponse>>() {}
        val response = testRestTemplate.exchange("/api-admin/v1/coupons", HttpMethod.POST, HttpEntity(body, jsonHeaders), responseType)
        return response.body!!.data!!.id
    }

    @DisplayName("POST /api/v1/coupons/{couponId}/issue")
    @Nested
    inner class Issue {
        @DisplayName("X-USER-ID 헤더와 유효한 템플릿으로 발급하면, AVAILABLE 상태로 발급된다.")
        @Test
        fun issuesCoupon() {
            val templateId = createTemplate()

            val responseType = object : ParameterizedTypeReference<ApiResponse<CouponV1Dto.IssuedCouponResponse>>() {}
            val response =
                testRestTemplate.exchange("/api/v1/coupons/$templateId/issue", HttpMethod.POST, HttpEntity(null, userHeaders(userId)), responseType)

            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.couponId).isEqualTo(templateId) },
                { assertThat(response.body?.data?.status).isEqualTo(IssuedCouponStatus.AVAILABLE) },
            )
        }

        @DisplayName("X-USER-ID 헤더가 없으면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenUserHeaderMissing() {
            val templateId = createTemplate()

            val responseType = object : ParameterizedTypeReference<ApiResponse<CouponV1Dto.IssuedCouponResponse>>() {}
            val response =
                testRestTemplate.exchange("/api/v1/coupons/$templateId/issue", HttpMethod.POST, HttpEntity(null, jsonHeaders), responseType)

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("존재하지 않는 템플릿으로 발급하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenTemplateMissing() {
            val responseType = object : ParameterizedTypeReference<ApiResponse<CouponV1Dto.IssuedCouponResponse>>() {}
            val response =
                testRestTemplate.exchange("/api/v1/coupons/999999/issue", HttpMethod.POST, HttpEntity(null, userHeaders(userId)), responseType)

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("동일 사용자가 같은 템플릿을 두 번 발급하면, 두 번째는 409 CONFLICT 응답을 받는다.")
        @Test
        fun returnsConflict_whenDuplicateIssue() {
            val templateId = createTemplate()
            val responseType = object : ParameterizedTypeReference<ApiResponse<CouponV1Dto.IssuedCouponResponse>>() {}
            testRestTemplate.exchange("/api/v1/coupons/$templateId/issue", HttpMethod.POST, HttpEntity(null, userHeaders(userId)), responseType)

            val response =
                testRestTemplate.exchange("/api/v1/coupons/$templateId/issue", HttpMethod.POST, HttpEntity(null, userHeaders(userId)), responseType)

            assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        }
    }

    @DisplayName("GET /api/v1/users/me/coupons")
    @Nested
    inner class GetMyCoupons {
        @DisplayName("발급받은 쿠폰이 있으면, 보유 쿠폰 목록을 표시 상태와 함께 반환한다.")
        @Test
        fun returnsMyCoupons() {
            val templateId = createTemplate()
            val issueType = object : ParameterizedTypeReference<ApiResponse<CouponV1Dto.IssuedCouponResponse>>() {}
            testRestTemplate.exchange("/api/v1/coupons/$templateId/issue", HttpMethod.POST, HttpEntity(null, userHeaders(userId)), issueType)

            val listType = object : ParameterizedTypeReference<ApiResponse<List<CouponV1Dto.OwnedCouponResponse>>>() {}
            val response =
                testRestTemplate.exchange("/api/v1/users/me/coupons", HttpMethod.GET, HttpEntity(null, userHeaders(userId)), listType)

            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data).hasSize(1) },
                { assertThat(response.body?.data?.first()?.couponId).isEqualTo(templateId) },
                { assertThat(response.body?.data?.first()?.status).isEqualTo(IssuedCouponStatus.AVAILABLE) },
            )
        }

        @DisplayName("발급 이력이 없으면, 빈 목록을 반환한다.")
        @Test
        fun returnsEmpty_whenNothingIssued() {
            val listType = object : ParameterizedTypeReference<ApiResponse<List<CouponV1Dto.OwnedCouponResponse>>>() {}
            val response =
                testRestTemplate.exchange("/api/v1/users/me/coupons", HttpMethod.GET, HttpEntity(null, userHeaders(userId)), listType)

            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data).isEmpty() },
            )
        }

        @DisplayName("X-USER-ID 헤더가 없으면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenUserHeaderMissing() {
            val listType = object : ParameterizedTypeReference<ApiResponse<List<CouponV1Dto.OwnedCouponResponse>>>() {}
            val response =
                testRestTemplate.exchange("/api/v1/users/me/coupons", HttpMethod.GET, HttpEntity(null, jsonHeaders), listType)

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }
}
