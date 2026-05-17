package com.loopers.domain.user

import com.loopers.domain.user.vo.BirthDate
import com.loopers.domain.user.vo.Name
import com.loopers.domain.user.vo.Password
import com.loopers.domain.user.vo.PhoneNumber
import com.loopers.domain.user.vo.RawPassword
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.time.LocalDate

class UserModelTest {
    private val encoder = BCryptPasswordEncoder()
    private val birthDate = BirthDate(LocalDate.of(1990, 1, 15))
    private val currentPlain = "Current1!"

    private fun newUser(): UserModel {
        val raw = RawPassword(currentPlain, birthDate)
        return UserModel(
            loginId = "tester01",
            password = Password.encode(raw, encoder),
            name = Name("홍길동"),
            birthDate = birthDate,
            email = "test@example.com",
            phoneNumber = PhoneNumber("010-1234-5678"),
        )
    }

    @DisplayName("회원을 생성할 때,")
    @Nested
    inner class Create {
        @DisplayName("필수 정보가 유효하면 정상 생성된다.")
        @Test
        fun createsUser_whenAllFieldsAreValid() {
            val user = newUser()

            assertAll(
                { assertThat(user.loginId).isEqualTo("tester01") },
                { assertThat(user.name.value).isEqualTo("홍길동") },
                { assertThat(user.email).isEqualTo("test@example.com") },
            )
        }

        @DisplayName("loginId가 한글/특수문자를 포함하면 BAD_REQUEST 예외가 발생한다 (AC-3).")
        @Test
        fun throwsBadRequest_whenLoginIdContainsInvalidChars() {
            val raw = RawPassword(currentPlain, birthDate)
            val ex = assertThrows<CoreException> {
                UserModel(
                    loginId = "한글ID",
                    password = Password.encode(raw, encoder),
                    name = Name("홍길동"),
                    birthDate = birthDate,
                    email = "test@example.com",
                    phoneNumber = PhoneNumber("010-1234-5678"),
                )
            }
            assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("비밀번호를 변경할 때,")
    @Nested
    inner class ChangePassword {
        @DisplayName("현재 비밀번호가 일치하고 새 비밀번호가 정책을 만족하면 변경된다.")
        @Test
        fun changesPassword_whenCurrentMatchesAndNewIsValid() {
            val user = newUser()
            val newRaw = RawPassword("NewPass99!", birthDate)

            user.changePassword(newRaw, currentPlain, encoder)

            assertThat(user.password.matches("NewPass99!", encoder)).isTrue()
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 동일하면 BAD_REQUEST 예외가 발생한다 (AC-33).")
        @Test
        fun throwsBadRequest_whenNewPasswordEqualsCurrent() {
            val user = newUser()
            val newRaw = RawPassword(currentPlain, birthDate)

            val ex = assertThrows<CoreException> {
                user.changePassword(newRaw, currentPlain, encoder)
            }
            assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
