package com.loopers.domain.user.vo

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PhoneNumberTest {
    @DisplayName("010-XXXX-XXXX 형식의 휴대폰 번호는 정상 생성된다")
    @Test
    fun createsPhoneNumber_whenValidFormat() {
        val phone = PhoneNumber("010-1234-5678")
        assertThat(phone.value).isEqualTo("010-1234-5678")
    }

    // AC-6
    @DisplayName("정해진 자릿수를 벗어나는 휴대폰 번호는 거부된다")
    @Test
    fun throwsBadRequest_whenDigitsAreInvalid() {
        val ex = assertThrows<CoreException> { PhoneNumber("010-12345-6789") }
        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    // AC-7
    @DisplayName("하이픈이 빠진 휴대폰 번호는 거부된다")
    @Test
    fun throwsBadRequest_whenHyphenMissing() {
        val ex = assertThrows<CoreException> { PhoneNumber("01012345678") }
        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    @DisplayName("앞자리가 010이 아닌 번호는 거부된다")
    @Test
    fun throwsBadRequest_whenPrefixIsNot010() {
        val ex = assertThrows<CoreException> { PhoneNumber("011-1234-5678") }
        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    // AC-22
    @DisplayName("휴대폰 번호의 가운데 4자리는 ****로 마스킹된다")
    @Test
    fun masksMiddleFourDigits() {
        assertThat(PhoneNumber("010-1234-5678").masked()).isEqualTo("010-****-5678")
    }
}
