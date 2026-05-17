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

    @DisplayName("8자 이상 16자 이하의 화이트리스트 문자만 포함하면 정상 생성된다 (AC-10).")
    @Test
    fun createsRawPassword_whenLengthIsEight() {
        assertDoesNotThrow { RawPassword("abcd1234", birthDate) }
    }

    @DisplayName("16자 경계값에서도 정상 생성된다 (AC-11).")
    @Test
    fun createsRawPassword_whenLengthIsSixteen() {
        assertDoesNotThrow { RawPassword("Abcdef1234!@#$%^", birthDate) }
    }

    @DisplayName("7자 비밀번호는 거부된다 (AC-9, AC-31).")
    @Test
    fun throwsBadRequest_whenLengthIsSeven() {
        val ex = assertThrows<CoreException> { RawPassword("abc1234", birthDate) }
        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    @DisplayName("17자 비밀번호는 거부된다 (AC-12).")
    @Test
    fun throwsBadRequest_whenLengthIsSeventeen() {
        val ex = assertThrows<CoreException> { RawPassword("Abcdef1234!@#$%^&", birthDate) }
        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    @DisplayName("비밀번호에 한글이 포함되면 거부된다 (AC-13).")
    @Test
    fun throwsBadRequest_whenPasswordContainsKorean() {
        val ex = assertThrows<CoreException> { RawPassword("abcd1234가", birthDate) }
        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    @DisplayName("비밀번호에 공백이 포함되면 거부된다 (AC-14).")
    @Test
    fun throwsBadRequest_whenPasswordContainsWhitespace() {
        val ex = assertThrows<CoreException> { RawPassword("abcd 1234", birthDate) }
        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    @DisplayName("yyyyMMdd 포맷의 생년월일이 포함되면 거부된다 (AC-15).")
    @Test
    fun throwsBadRequest_whenPasswordContainsBirthDateYyyyMmDd() {
        val ex = assertThrows<CoreException> { RawPassword("aa19900115", birthDate) }
        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    @DisplayName("yyyy-MM-dd 포맷의 생년월일이 포함되면 거부된다 (AC-16).")
    @Test
    fun throwsBadRequest_whenPasswordContainsBirthDateYyyyDashMmDashDd() {
        val ex = assertThrows<CoreException> { RawPassword("a1990-01-15", birthDate) }
        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    @DisplayName("생년월일 부분 일치(9001)는 허용된다 (AC-17).")
    @Test
    fun createsRawPassword_whenPartialBirthMatch() {
        assertDoesNotThrow { RawPassword("abc9001de", birthDate) }
    }

    @DisplayName("특수문자만으로도 정상 생성된다.")
    @Test
    fun createsRawPassword_withPunctuationOnly() {
        assertDoesNotThrow { RawPassword("!@#$%^&*", birthDate) }
    }
}
