package com.loopers.interfaces.api

import com.loopers.domain.user.UserService
import com.loopers.domain.user.vo.BirthDate
import com.loopers.domain.user.vo.Name
import com.loopers.domain.user.vo.PhoneNumber
import com.loopers.domain.user.vo.RawPassword
import com.loopers.interfaces.api.user.UserV1Dto
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
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userService: UserService,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val SIGN_UP_URL = "/api/v1/users"
        private const val ME_URL = "/api/v1/users/me"
        private const val CHANGE_PASSWORD_URL = "/api/v1/users/me/password"
        private const val HEADER_LOGIN_ID = "X-Loopers-LoginId"
        private const val HEADER_LOGIN_PW = "X-Loopers-LoginPw"
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private val birthDate = BirthDate(LocalDate.of(1990, 1, 15))

    private fun seedUser(
        loginId: String = "tester01",
        plainPassword: String = "Pass1234!",
        nameValue: String = "홍길동",
        phone: String = "010-1234-5678",
    ) {
        userService.signUp(
            loginId = loginId,
            rawPassword = RawPassword(plainPassword, birthDate),
            name = Name(nameValue),
            birthDate = birthDate,
            email = "test@example.com",
            phoneNumber = PhoneNumber(phone),
        )
    }

    private fun authHeaders(loginId: String, plainPassword: String): HttpHeaders {
        val headers = HttpHeaders()
        headers.set(HEADER_LOGIN_ID, loginId)
        headers.set(HEADER_LOGIN_PW, plainPassword)
        return headers
    }

    private fun validSignUpBody(
        loginId: String = "tester01",
        password: String = "Pass1234!",
        name: String = "홍길동",
        birth: String = "1990-01-15",
        email: String = "test@example.com",
        phoneNumber: String = "010-1234-5678",
    ): Map<String, Any> = mapOf(
        "loginId" to loginId,
        "password" to password,
        "name" to name,
        "birthDate" to birth,
        "email" to email,
        "phoneNumber" to phoneNumber,
    )

    @DisplayName("POST /api/v1/users")
    @Nested
    inner class SignUp {
        @DisplayName("AC-1: 유효한 6개 필드를 보내면 201 + loginId 응답을 반환한다.")
        @Test
        fun returnsCreatedWithLoginId_whenValid() {
            val body = validSignUpBody()
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>>() {}

            val response = testRestTemplate.exchange(SIGN_UP_URL, HttpMethod.POST, HttpEntity(body), responseType)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED) },
                { assertThat(response.body?.data?.loginId).isEqualTo("tester01") },
            )
        }

        @DisplayName("AC-2: 응답 본문에 비밀번호 평문/해시가 포함되지 않는다 (NFR-3).")
        @Test
        fun responseDoesNotIncludePassword() {
            val body = validSignUpBody(password = "Pass1234!")
            val stringResponseType = object : ParameterizedTypeReference<String>() {}

            val response = testRestTemplate.exchange(SIGN_UP_URL, HttpMethod.POST, HttpEntity(body), stringResponseType)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED) },
                { assertThat(response.body).doesNotContain("Pass1234!") },
                { assertThat(response.body).doesNotContain("password") },
                { assertThat(response.body).doesNotContain("hashedValue") },
                { assertThat(response.body).doesNotContain("\$2") },
            )
        }

        @DisplayName("AC-3: loginId에 한글 포함 → 400.")
        @Test
        fun returnsBadRequest_whenLoginIdContainsKorean() {
            val body = validSignUpBody(loginId = "한글ID")
            val response = postSignUp(body)
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("AC-4: 이메일에 @가 없으면 400.")
        @Test
        fun returnsBadRequest_whenEmailHasNoAt() {
            val body = validSignUpBody(email = "invalid-email")
            val response = postSignUp(body)
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("AC-5: 존재하지 않는 생년월일(1999-02-31) → 400.")
        @Test
        fun returnsBadRequest_whenBirthDateIsInvalid() {
            val body = validSignUpBody(birth = "1999-02-31")
            val response = postSignUp(body)
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("AC-6: 휴대폰 자릿수 위반 → 400.")
        @Test
        fun returnsBadRequest_whenPhoneDigitsInvalid() {
            val body = validSignUpBody(phoneNumber = "010-12345-6789")
            val response = postSignUp(body)
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("AC-7: 휴대폰 하이픈 누락 → 400.")
        @Test
        fun returnsBadRequest_whenPhoneHasNoHyphen() {
            val body = validSignUpBody(phoneNumber = "01012345678")
            val response = postSignUp(body)
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("AC-8: 이름 빈 문자열 → 400.")
        @Test
        fun returnsBadRequest_whenNameIsBlank() {
            val body = validSignUpBody(name = "")
            val response = postSignUp(body)
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("AC-9: 비밀번호 7자 → 400.")
        @Test
        fun returnsBadRequest_whenPasswordIsSevenChars() {
            val body = validSignUpBody(password = "Pas123!")
            val response = postSignUp(body)
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("AC-10: 비밀번호 8자(경계) → 201.")
        @Test
        fun returnsCreated_whenPasswordIsEightChars() {
            val body = validSignUpBody(password = "Pass123!")
            val response = postSignUp(body)
            assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
        }

        @DisplayName("AC-15: 비밀번호에 생년월일(yyyyMMdd) 포함 → 400.")
        @Test
        fun returnsBadRequest_whenPasswordContainsBirthYyyyMmDd() {
            val body = validSignUpBody(password = "ab19900115")
            val response = postSignUp(body)
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("AC-18: 이미 가입된 loginId로 가입 → 409 + DUPLICATE_LOGIN_ID.")
        @Test
        fun returnsConflict_whenLoginIdDuplicate() {
            postSignUp(validSignUpBody(loginId = "dupId"))

            val response = postSignUp(validSignUpBody(loginId = "dupId"))

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("DUPLICATE_LOGIN_ID") },
            )
        }

        private fun postSignUp(body: Map<String, Any>): org.springframework.http.ResponseEntity<ApiResponse<UserV1Dto.SignUpResponse>> {
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>>() {}
            return testRestTemplate.exchange(SIGN_UP_URL, HttpMethod.POST, HttpEntity(body), responseType)
        }
    }

    @DisplayName("GET /api/v1/users/me")
    @Nested
    inner class GetMyInfo {
        @DisplayName("AC-19/20/22: 유효한 헤더 → 200, 이름·휴대폰 마스킹.")
        @Test
        fun returnsOkWithMaskedFields() {
            seedUser(nameValue = "홍길동", phone = "010-1234-5678")
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserInfoResponse>>() {}

            val response = testRestTemplate.exchange(
                ME_URL,
                HttpMethod.GET,
                HttpEntity<Unit>(authHeaders("tester01", "Pass1234!")),
                responseType,
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.loginId).isEqualTo("tester01") },
                { assertThat(response.body?.data?.name).isEqualTo("홍길*") },
                { assertThat(response.body?.data?.phoneNumber).isEqualTo("010-****-5678") },
                { assertThat(response.body?.data?.email).isEqualTo("test@example.com") },
            )
        }

        @DisplayName("AC-21: 1글자 이름은 *로 마스킹된다.")
        @Test
        fun masksSingleCharNameAsAsterisk() {
            seedUser(nameValue = "이")
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserInfoResponse>>() {}

            val response = testRestTemplate.exchange(
                ME_URL,
                HttpMethod.GET,
                HttpEntity<Unit>(authHeaders("tester01", "Pass1234!")),
                responseType,
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.name).isEqualTo("*") },
            )
        }

        @DisplayName("AC-23: X-Loopers-LoginId 헤더 누락 → 400.")
        @Test
        fun returnsBadRequest_whenLoginIdHeaderMissing() {
            seedUser()
            val headers = HttpHeaders().apply { set(HEADER_LOGIN_PW, "Pass1234!") }
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserInfoResponse>>() {}

            val response = testRestTemplate.exchange(ME_URL, HttpMethod.GET, HttpEntity<Unit>(headers), responseType)

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("AC-24: X-Loopers-LoginPw 헤더 누락 → 400.")
        @Test
        fun returnsBadRequest_whenLoginPwHeaderMissing() {
            seedUser()
            val headers = HttpHeaders().apply { set(HEADER_LOGIN_ID, "tester01") }
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserInfoResponse>>() {}

            val response = testRestTemplate.exchange(ME_URL, HttpMethod.GET, HttpEntity<Unit>(headers), responseType)

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("AC-25: 존재하지 않는 loginId 헤더 → 401.")
        @Test
        fun returnsUnauthorized_whenLoginIdNotExists() {
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserInfoResponse>>() {}

            val response = testRestTemplate.exchange(
                ME_URL,
                HttpMethod.GET,
                HttpEntity<Unit>(authHeaders("nobody", "Pass1234!")),
                responseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @DisplayName("AC-26: 존재하는 loginId + 잘못된 비번 → 401.")
        @Test
        fun returnsUnauthorized_whenPasswordMismatch() {
            seedUser()
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserInfoResponse>>() {}

            val response = testRestTemplate.exchange(
                ME_URL,
                HttpMethod.GET,
                HttpEntity<Unit>(authHeaders("tester01", "WrongPass1!")),
                responseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @DisplayName("AC-27: 응답 본문에 비밀번호가 포함되지 않는다.")
        @Test
        fun responseDoesNotIncludePassword() {
            seedUser(plainPassword = "Pass1234!")
            val stringResponseType = object : ParameterizedTypeReference<String>() {}

            val response = testRestTemplate.exchange(
                ME_URL,
                HttpMethod.GET,
                HttpEntity<Unit>(authHeaders("tester01", "Pass1234!")),
                stringResponseType,
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body).doesNotContain("Pass1234!") },
                { assertThat(response.body).doesNotContain("hashedValue") },
                { assertThat(response.body).doesNotContain("\$2") },
            )
        }
    }

    @DisplayName("PATCH /api/v1/users/me/password")
    @Nested
    inner class ChangePassword {
        @DisplayName("AC-28: 유효한 헤더 + 정책 통과 newPassword → 200, data:null.")
        @Test
        fun returnsOk_whenValid() {
            seedUser(plainPassword = "OldPass1!")
            val responseType = object : ParameterizedTypeReference<ApiResponse<Unit>>() {}

            val response = testRestTemplate.exchange(
                CHANGE_PASSWORD_URL,
                HttpMethod.PATCH,
                HttpEntity(mapOf("newPassword" to "NewPass99!"), authHeaders("tester01", "OldPass1!")),
                responseType,
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data).isNull() },
            )
        }

        @DisplayName("AC-29/30: 변경 후 새 비번 200, 기존 비번 401.")
        @Test
        fun authenticationStateSwitches_afterChange() {
            seedUser(plainPassword = "OldPass1!")
            val unitType = object : ParameterizedTypeReference<ApiResponse<Unit>>() {}
            val infoType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserInfoResponse>>() {}

            testRestTemplate.exchange(
                CHANGE_PASSWORD_URL,
                HttpMethod.PATCH,
                HttpEntity(mapOf("newPassword" to "NewPass99!"), authHeaders("tester01", "OldPass1!")),
                unitType,
            )

            val withNew = testRestTemplate.exchange(
                ME_URL,
                HttpMethod.GET,
                HttpEntity<Unit>(authHeaders("tester01", "NewPass99!")),
                infoType,
            )
            val withOld = testRestTemplate.exchange(
                ME_URL,
                HttpMethod.GET,
                HttpEntity<Unit>(authHeaders("tester01", "OldPass1!")),
                infoType,
            )

            assertAll(
                { assertThat(withNew.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(withOld.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED) },
            )
        }

        @DisplayName("AC-32: newPassword에 생년월일(yyyyMMdd) 포함 → 400.")
        @Test
        fun returnsBadRequest_whenNewPasswordContainsBirthDate() {
            seedUser(plainPassword = "OldPass1!")
            val responseType = object : ParameterizedTypeReference<ApiResponse<Unit>>() {}

            val response = testRestTemplate.exchange(
                CHANGE_PASSWORD_URL,
                HttpMethod.PATCH,
                HttpEntity(mapOf("newPassword" to "ab19900115!"), authHeaders("tester01", "OldPass1!")),
                responseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("AC-33: newPassword가 현재 비번과 동일 → 400.")
        @Test
        fun returnsBadRequest_whenNewPasswordEqualsCurrent() {
            seedUser(plainPassword = "SamePass1!")
            val responseType = object : ParameterizedTypeReference<ApiResponse<Unit>>() {}

            val response = testRestTemplate.exchange(
                CHANGE_PASSWORD_URL,
                HttpMethod.PATCH,
                HttpEntity(mapOf("newPassword" to "SamePass1!"), authHeaders("tester01", "SamePass1!")),
                responseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("AC-34: 헤더 비번 불일치 상태에서 변경 시도 → 401.")
        @Test
        fun returnsUnauthorized_whenHeaderPasswordMismatch() {
            seedUser(plainPassword = "OldPass1!")
            val responseType = object : ParameterizedTypeReference<ApiResponse<Unit>>() {}

            val response = testRestTemplate.exchange(
                CHANGE_PASSWORD_URL,
                HttpMethod.PATCH,
                HttpEntity(mapOf("newPassword" to "NewPass99!"), authHeaders("tester01", "WrongPass1!")),
                responseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @DisplayName("AC-35: 헤더 누락 상태에서 변경 시도 → 400.")
        @Test
        fun returnsBadRequest_whenHeaderMissing() {
            seedUser(plainPassword = "OldPass1!")
            val responseType = object : ParameterizedTypeReference<ApiResponse<Unit>>() {}

            val response = testRestTemplate.exchange(
                CHANGE_PASSWORD_URL,
                HttpMethod.PATCH,
                HttpEntity(mapOf("newPassword" to "NewPass99!"), HttpHeaders()),
                responseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }
}
