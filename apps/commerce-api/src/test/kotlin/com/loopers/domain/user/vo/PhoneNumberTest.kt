package com.loopers.domain.user.vo

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PhoneNumberTest {
    @DisplayName("010-XXXX-XXXX 형식이면 정상 생성된다.")
    @Test
    fun createsPhoneNumber_whenValidFormat() {
        val phone = PhoneNumber("010-1234-5678")
        assertThat(phone.value).isEqualTo("010-1234-5678")
    }

    @DisplayName("자릿수가 잘못되면 거부된다 (AC-6).")
    @Test
    fun throwsBadRequest_whenDigitsAreInvalid() {
        val ex = assertThrows<CoreException> { PhoneNumber("010-12345-6789") }
        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    @DisplayName("하이픈이 없으면 거부된다 (AC-7).")
    @Test
    fun throwsBadRequest_whenHyphenMissing() {
        val ex = assertThrows<CoreException> { PhoneNumber("01012345678") }
        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    @DisplayName("010 외 prefix는 거부된다.")
    @Test
    fun throwsBadRequest_whenPrefixIsNot010() {
        val ex = assertThrows<CoreException> { PhoneNumber("011-1234-5678") }
        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    @DisplayName("가운데 4자리를 ****로 마스킹한다 (AC-22).")
    @Test
    fun masksMiddleFourDigits() {
        assertThat(PhoneNumber("010-1234-5678").masked()).isEqualTo("010-****-5678")
    }
}
