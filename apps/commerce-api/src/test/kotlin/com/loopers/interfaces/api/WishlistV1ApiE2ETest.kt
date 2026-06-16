package com.loopers.interfaces.api

import com.loopers.interfaces.api.wishlist.WishlistV1Dto
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
class WishlistV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val propertyId = 1024L
    private val baseUrl = "/api/v1/properties/$propertyId/wishlist"

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun userHeaders(userId: Long): HttpHeaders =
        HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-USER-ID", userId.toString())
        }

    private val wishlistType = object : ParameterizedTypeReference<ApiResponse<WishlistV1Dto.WishlistResponse>>() {}

    @DisplayName("POST /api/v1/properties/{propertyId}/wishlist")
    @Nested
    inner class Add {
        @DisplayName("찜하면, 찜 수 1을 반환한다.")
        @Test
        fun addsWishlist() {
            val response = testRestTemplate.exchange(baseUrl, HttpMethod.POST, HttpEntity(null, userHeaders(1L)), wishlistType)

            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.propertyId).isEqualTo(propertyId) },
                { assertThat(response.body?.data?.wishlistCount).isEqualTo(1L) },
            )
        }

        @DisplayName("X-USER-ID 헤더가 없으면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenUserHeaderMissing() {
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
            val response = testRestTemplate.exchange(baseUrl, HttpMethod.POST, HttpEntity(null, headers), wishlistType)

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    @DisplayName("DELETE /api/v1/properties/{propertyId}/wishlist")
    @Nested
    inner class Remove {
        @DisplayName("찜을 취소하면, 찜 수가 0으로 감소한다.")
        @Test
        fun removesWishlist() {
            testRestTemplate.exchange(baseUrl, HttpMethod.POST, HttpEntity(null, userHeaders(1L)), wishlistType)

            val response = testRestTemplate.exchange(baseUrl, HttpMethod.DELETE, HttpEntity(null, userHeaders(1L)), wishlistType)

            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.wishlistCount).isEqualTo(0L) },
            )
        }
    }

    @DisplayName("GET /api/v1/properties/{propertyId}/wishlist/count")
    @Nested
    inner class Count {
        @DisplayName("여러 사용자가 찜하면, 누적 찜 수를 반환한다.")
        @Test
        fun returnsCount() {
            testRestTemplate.exchange(baseUrl, HttpMethod.POST, HttpEntity(null, userHeaders(1L)), wishlistType)
            testRestTemplate.exchange(baseUrl, HttpMethod.POST, HttpEntity(null, userHeaders(2L)), wishlistType)

            val response = testRestTemplate.exchange("$baseUrl/count", HttpMethod.GET, HttpEntity(null, HttpHeaders()), wishlistType)

            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.wishlistCount).isEqualTo(2L) },
            )
        }
    }
}
