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
        // AC-1
        @DisplayName("유효한 회원 정보로 가입 요청하면 201과 loginId를 반환한다")
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

        // AC-2, NFR-3
        @DisplayName("회원 가입 응답 본문에는 비밀번호 평문이나 해시가 포함되지 않는다")
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

        // AC-3
        @DisplayName("loginId에 한글이 포함되면 회원 가입이 거부된다")
        @Test
        fun returnsBadRequest_whenLoginIdContainsKorean() {
            val body = validSignUpBody(loginId = "한글ID")
            val response = postSignUp(body)
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        // AC-4
        @DisplayName("이메일 형식이 올바르지 않으면 회원 가입이 거부된다")
        @Test
        fun returnsBadRequest_whenEmailHasNoAt() {
            val body = validSignUpBody(email = "invalid-email")
            val response = postSignUp(body)
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        // AC-5
        @DisplayName("실재하지 않는 날짜를 생년월일로 보내면 회원 가입이 거부된다")
        @Test
        fun returnsBadRequest_whenBirthDateIsInvalid() {
            val body = validSignUpBody(birth = "1999-02-31")
            val response = postSignUp(body)
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        // AC-6
        @DisplayName("휴대폰 자릿수가 형식을 벗어나면 회원 가입이 거부된다")
        @Test
        fun returnsBadRequest_whenPhoneDigitsInvalid() {
            val body = validSignUpBody(phoneNumber = "010-12345-6789")
            val response = postSignUp(body)
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        // AC-7
        @DisplayName("휴대폰 번호에 하이픈이 없으면 회원 가입이 거부된다")
        @Test
        fun returnsBadRequest_whenPhoneHasNoHyphen() {
            val body = validSignUpBody(phoneNumber = "01012345678")
            val response = postSignUp(body)
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        // AC-8
        @DisplayName("이름이 빈 문자열이면 회원 가입이 거부된다")
        @Test
        fun returnsBadRequest_whenNameIsBlank() {
            val body = validSignUpBody(name = "")
            val response = postSignUp(body)
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        // AC-9
        @DisplayName("비밀번호가 최소 길이(8자) 미만이면 회원 가입이 거부된다")
        @Test
        fun returnsBadRequest_whenPasswordIsSevenChars() {
            val body = validSignUpBody(password = "Pas123!")
            val response = postSignUp(body)
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        // AC-10
        @DisplayName("비밀번호가 정확히 최소 길이(8자)면 회원 가입이 성공한다")
        @Test
        fun returnsCreated_whenPasswordIsEightChars() {
            val body = validSignUpBody(password = "Pass123!")
            val response = postSignUp(body)
            assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
        }

        // AC-15
        @DisplayName("비밀번호에 생년월일이 포함되면 회원 가입이 거부된다")
        @Test
        fun returnsBadRequest_whenPasswordContainsBirthYyyyMmDd() {
            val body = validSignUpBody(password = "ab19900115")
            val response = postSignUp(body)
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        // AC-18
        @DisplayName("이미 가입된 loginId로 가입을 시도하면 DUPLICATE_LOGIN_ID 오류로 차단된다")
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
        // AC-19, AC-20, AC-22
        @DisplayName("유효한 인증 헤더로 내 정보를 조회하면 이름과 휴대폰이 마스킹된 응답을 받는다")
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

        // AC-21
        @DisplayName("한 글자 이름은 내 정보 조회 응답에서 *로 마스킹된다")
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

        // AC-23
        @DisplayName("loginId 인증 헤더가 누락되면 내 정보 조회 요청이 거부된다")
        @Test
        fun returnsBadRequest_whenLoginIdHeaderMissing() {
            seedUser()
            val headers = HttpHeaders().apply { set(HEADER_LOGIN_PW, "Pass1234!") }
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserInfoResponse>>() {}

            val response = testRestTemplate.exchange(ME_URL, HttpMethod.GET, HttpEntity<Unit>(headers), responseType)

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        // AC-24
        @DisplayName("loginPw 인증 헤더가 누락되면 내 정보 조회 요청이 거부된다")
        @Test
        fun returnsBadRequest_whenLoginPwHeaderMissing() {
            seedUser()
            val headers = HttpHeaders().apply { set(HEADER_LOGIN_ID, "tester01") }
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserInfoResponse>>() {}

            val response = testRestTemplate.exchange(ME_URL, HttpMethod.GET, HttpEntity<Unit>(headers), responseType)

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        // AC-25
        @DisplayName("존재하지 않는 loginId로 내 정보를 조회하면 인증 실패로 응답한다")
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

        // AC-26
        @DisplayName("존재하는 loginId라도 비밀번호가 일치하지 않으면 인증 실패로 응답한다")
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

        // AC-27
        @DisplayName("내 정보 조회 응답 본문에는 비밀번호 평문이나 해시가 포함되지 않는다")
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
        // AC-28
        @DisplayName("유효한 인증과 정책을 만족하는 새 비밀번호로 요청하면 비밀번호 변경이 성공한다")
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

        // AC-29, AC-30
        @DisplayName("비밀번호 변경 직후에는 새 비밀번호로만 인증이 성공하고 기존 비밀번호로는 실패한다")
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

        // AC-32
        @DisplayName("새 비밀번호에 생년월일이 포함되면 비밀번호 변경이 거부된다")
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

        // AC-33
        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면 비밀번호 변경이 거부된다")
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

        // AC-34
        @DisplayName("인증 헤더의 비밀번호가 일치하지 않으면 비밀번호 변경 요청은 인증 실패로 응답한다")
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

        // AC-35
        @DisplayName("인증 헤더가 누락되면 비밀번호 변경 요청이 거부된다")
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
