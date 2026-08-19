package com.loopers.interfaces.api

import com.loopers.domain.coupon.CouponType
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
class CouponAdminV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val baseUrl = "/api-admin/v1/coupons"
    private val jsonHeaders = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private val couponType = object : ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponResponse>>() {}

    private fun createRate10(name: String = "여름 10% 할인"): Long {
        val body = """{"name":"$name","type":"RATE","value":10,"minOrderAmount":100000,"expiredAt":"2026-12-31T23:59:59"}"""
        val response = testRestTemplate.exchange(baseUrl, HttpMethod.POST, HttpEntity(body, jsonHeaders), couponType)
        return response.body!!.data!!.id
    }

    @DisplayName("POST /api-admin/v1/coupons")
    @Nested
    inner class Create {
        @DisplayName("정률 쿠폰 템플릿을 등록하면, 생성된 템플릿을 반환한다.")
        @Test
        fun createsRateTemplate() {
            val body = """{"name":"여름 10% 할인","type":"RATE","value":10,"minOrderAmount":100000,"expiredAt":"2026-12-31T23:59:59"}"""

            val response = testRestTemplate.exchange(baseUrl, HttpMethod.POST, HttpEntity(body, jsonHeaders), couponType)

            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.id).isNotNull() },
                { assertThat(response.body?.data?.type).isEqualTo(CouponType.RATE) },
                { assertThat(response.body?.data?.value).isEqualTo(10) },
            )
        }

        @DisplayName("정률 비율이 100을 초과하면, 400 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenRateOver100() {
            val body = """{"name":"잘못된 쿠폰","type":"RATE","value":150,"expiredAt":"2026-12-31T23:59:59"}"""

            val response = testRestTemplate.exchange(baseUrl, HttpMethod.POST, HttpEntity(body, jsonHeaders), couponType)

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    @DisplayName("GET /api-admin/v1/coupons")
    @Nested
    inner class Read {
        @DisplayName("상세 조회하면, 등록한 템플릿을 반환한다.")
        @Test
        fun getsTemplate() {
            val id = createRate10()

            val response = testRestTemplate.exchange("$baseUrl/$id", HttpMethod.GET, HttpEntity(null, jsonHeaders), couponType)

            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.id).isEqualTo(id) },
            )
        }

        @DisplayName("목록 조회하면, 페이지 응답을 반환한다.")
        @Test
        fun listsTemplates() {
            createRate10("쿠폰 A")
            createRate10("쿠폰 B")

            val pageType = object :
                ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.PageResponse<CouponAdminV1Dto.CouponResponse>>>() {}
            val response = testRestTemplate.exchange("$baseUrl?page=0&size=20", HttpMethod.GET, HttpEntity(null, jsonHeaders), pageType)

            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.totalElements).isEqualTo(2) },
                { assertThat(response.body?.data?.content).hasSize(2) },
            )
        }
    }

    @DisplayName("PUT /api-admin/v1/coupons/{id}")
    @Nested
    inner class Update {
        @DisplayName("템플릿을 수정하면, 변경된 값을 반환한다.")
        @Test
        fun updatesTemplate() {
            val id = createRate10()
            val body = """{"name":"수정된 쿠폰","type":"FIXED","value":5000,"expiredAt":"2027-01-31T23:59:59"}"""

            val response = testRestTemplate.exchange("$baseUrl/$id", HttpMethod.PUT, HttpEntity(body, jsonHeaders), couponType)

            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.name).isEqualTo("수정된 쿠폰") },
                { assertThat(response.body?.data?.type).isEqualTo(CouponType.FIXED) },
            )
        }
    }

    @DisplayName("DELETE /api-admin/v1/coupons/{id}")
    @Nested
    inner class Delete {
        @DisplayName("템플릿을 삭제하면, 이후 상세 조회는 404 를 반환한다(소프트 삭제).")
        @Test
        fun deletesTemplate() {
            val id = createRate10()

            val deleteResponse = testRestTemplate.exchange("$baseUrl/$id", HttpMethod.DELETE, HttpEntity(null, jsonHeaders), String::class.java)
            val getResponse = testRestTemplate.exchange("$baseUrl/$id", HttpMethod.GET, HttpEntity(null, jsonHeaders), couponType)

            assertAll(
                { assertThat(deleteResponse.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(getResponse.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
            )
        }
    }

    @DisplayName("GET /api-admin/v1/coupons/{id}/issues")
    @Nested
    inner class ListIssues {
        @DisplayName("발급된 쿠폰이 있으면, 발급 내역을 페이지로 반환한다.")
        @Test
        fun listsIssues() {
            val id = createRate10()
            // 사용자가 발급
            val userHeaders = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
                set("X-USER-ID", "1")
            }
            val issueType = object : ParameterizedTypeReference<ApiResponse<CouponV1Dto.IssuedCouponResponse>>() {}
            testRestTemplate.exchange("/api/v1/coupons/$id/issue", HttpMethod.POST, HttpEntity(null, userHeaders), issueType)

            val pageType = object :
                ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.PageResponse<CouponV1Dto.IssuedCouponResponse>>>() {}
            val response = testRestTemplate.exchange("$baseUrl/$id/issues?page=0&size=20", HttpMethod.GET, HttpEntity(null, jsonHeaders), pageType)

            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.totalElements).isEqualTo(1) },
                { assertThat(response.body?.data?.content?.first()?.couponId).isEqualTo(id) },
            )
        }
    }
}
