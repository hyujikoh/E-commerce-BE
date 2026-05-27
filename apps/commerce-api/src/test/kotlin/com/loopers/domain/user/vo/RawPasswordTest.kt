package com.loopers.domain.user.vo

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class RawPasswordTest {
    private val birthDate = BirthDate(LocalDate.of(1990, 1, 15))

    // AC-10
    @DisplayName("비밀번호가 정확히 최소 길이(8자)면 허용된다")
    @Test
    fun createsRawPassword_whenLengthIsEight() {
        assertDoesNotThrow { RawPassword("abcd1234", birthDate) }
    }

    // AC-11
    @DisplayName("비밀번호가 정확히 최대 길이(16자)면 허용된다")
    @Test
    fun createsRawPassword_whenLengthIsSixteen() {
        assertDoesNotThrow { RawPassword("Abcdef1234!@#$%^", birthDate) }
    }

    // AC-9, AC-31
    @DisplayName("비밀번호가 최소 길이(8자) 미만이면 거부된다")
    @Test
    fun throwsBadRequest_whenLengthIsSeven() {
        val ex = assertThrows<CoreException> { RawPassword("abc1234", birthDate) }
        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    // AC-12
    @DisplayName("비밀번호가 최대 길이(16자)를 초과하면 거부된다")
    @Test
    fun throwsBadRequest_whenLengthIsSeventeen() {
        val ex = assertThrows<CoreException> { RawPassword("Abcdef1234!@#$%^&", birthDate) }
        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    // AC-13
    @DisplayName("비밀번호에 한글이 포함되면 거부된다")
    @Test
    fun throwsBadRequest_whenPasswordContainsKorean() {
        val ex = assertThrows<CoreException> { RawPassword("abcd1234가", birthDate) }
        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    // AC-14
    @DisplayName("비밀번호에 공백 문자가 포함되면 거부된다")
    @Test
    fun throwsBadRequest_whenPasswordContainsWhitespace() {
        val ex = assertThrows<CoreException> { RawPassword("abcd 1234", birthDate) }
        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    // AC-15
    @DisplayName("비밀번호에 생년월일이 yyyyMMdd 형태로 포함되면 거부된다")
    @Test
    fun throwsBadRequest_whenPasswordContainsBirthDateYyyyMmDd() {
        val ex = assertThrows<CoreException> { RawPassword("aa19900115", birthDate) }
        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    // AC-16
    @DisplayName("비밀번호에 생년월일이 yyyy-MM-dd 형태로 포함되면 거부된다")
    @Test
    fun throwsBadRequest_whenPasswordContainsBirthDateYyyyDashMmDashDd() {
        val ex = assertThrows<CoreException> { RawPassword("a1990-01-15", birthDate) }
        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    // AC-17
    @DisplayName("생년월일의 일부 숫자만 포함된 비밀번호는 허용된다")
    @Test
    fun createsRawPassword_whenPartialBirthMatch() {
        assertDoesNotThrow { RawPassword("abc9001de", birthDate) }
    }

    @DisplayName("허용된 특수문자만으로 구성된 비밀번호도 허용된다")
    @Test
    fun createsRawPassword_withPunctuationOnly() {
        assertDoesNotThrow { RawPassword("!@#$%^&*", birthDate) }
    }
}
