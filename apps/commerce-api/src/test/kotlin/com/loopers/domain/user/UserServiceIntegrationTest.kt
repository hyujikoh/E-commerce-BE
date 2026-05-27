package com.loopers.domain.user

import com.loopers.domain.user.vo.BirthDate
import com.loopers.domain.user.vo.Name
import com.loopers.domain.user.vo.PhoneNumber
import com.loopers.domain.user.vo.RawPassword
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDate

@SpringBootTest
class UserServiceIntegrationTest @Autowired constructor(
    private val userService: UserService,
    private val userJpaRepository: UserJpaRepository,
    private val passwordEncoder: PasswordEncoder,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private val birthDate = BirthDate(LocalDate.of(1990, 1, 15))

    private fun signUpFixture(
        loginId: String = "tester01",
        password: String = "Pass1234!",
    ): UserModel = userService.signUp(
        loginId = loginId,
        rawPassword = RawPassword(password, birthDate),
        name = Name("홍길동"),
        birthDate = birthDate,
        email = "test@example.com",
        phoneNumber = PhoneNumber("010-1234-5678"),
    )

    @DisplayName("회원 가입할 때,")
    @Nested
    inner class SignUp {
        // AC-2
        @DisplayName("회원 가입 시 비밀번호는 BCrypt 해시로 변환되어 저장된다")
        @Test
        fun storesBcryptHash() {
            val plain = "Pass1234!"
            val user = signUpFixture(password = plain)

            val persisted = userJpaRepository.findById(user.id).orElseThrow()

            assertAll(
                { assertThat(persisted.password.hashedValue).startsWith("\$2") },
                { assertThat(persisted.password.hashedValue).isNotEqualTo(plain) },
                { assertThat(passwordEncoder.matches(plain, persisted.password.hashedValue)).isTrue() },
            )
        }

        // AC-18
        @DisplayName("이미 사용 중인 loginId로 가입하면 UNIQUE 제약 위반으로 차단된다")
        @Test
        fun throwsDataIntegrityViolation_whenLoginIdDuplicate() {
            signUpFixture(loginId = "duplicateId")

            assertThrows<DataIntegrityViolationException> {
                signUpFixture(loginId = "duplicateId")
            }
        }
    }

    @DisplayName("비밀번호를 변경할 때,")
    @Nested
    inner class ChangePassword {
        // AC-29
        @DisplayName("비밀번호 변경 후에는 새 비밀번호로 인증이 성공한다")
        @Test
        fun authenticatesWithNewPassword_afterChange() {
            signUpFixture(password = "OldPass1!")

            userService.changePassword(
                loginId = "tester01",
                currentPlainPassword = "OldPass1!",
                newPlainPassword = "NewPass99!",
            )

            val updated = userService.findByLoginId("tester01")
            assertThat(updated.password.matches("NewPass99!", passwordEncoder)).isTrue()
        }

        // AC-30
        @DisplayName("비밀번호 변경 후에는 기존 비밀번호로 인증할 수 없다")
        @Test
        fun rejectsOldPassword_afterChange() {
            signUpFixture(password = "OldPass1!")

            userService.changePassword(
                loginId = "tester01",
                currentPlainPassword = "OldPass1!",
                newPlainPassword = "NewPass99!",
            )

            val updated = userService.findByLoginId("tester01")
            assertThat(updated.password.matches("OldPass1!", passwordEncoder)).isFalse()
        }

        // AC-32
        @DisplayName("새 비밀번호에 생년월일이 포함되면 변경이 거부된다")
        @Test
        fun throwsBadRequest_whenNewPasswordContainsBirthDate() {
            signUpFixture(password = "OldPass1!")

            val ex = assertThrows<CoreException> {
                userService.changePassword(
                    loginId = "tester01",
                    currentPlainPassword = "OldPass1!",
                    newPlainPassword = "ab19900115!",
                )
            }
            assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        // AC-33
        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면 변경이 거부된다")
        @Test
        fun throwsBadRequest_whenNewPasswordEqualsCurrent() {
            signUpFixture(password = "SamePass1!")

            val ex = assertThrows<CoreException> {
                userService.changePassword(
                    loginId = "tester01",
                    currentPlainPassword = "SamePass1!",
                    newPlainPassword = "SamePass1!",
                )
            }
            assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
