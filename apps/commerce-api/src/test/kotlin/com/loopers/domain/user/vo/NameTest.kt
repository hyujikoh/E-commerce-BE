package com.loopers.domain.user.vo

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NameTest {
    // AC-8
    @DisplayName("빈 문자열로 이름을 만들면 거부된다")
    @Test
    fun throwsBadRequest_whenNameIsBlank() {
        val ex = assertThrows<CoreException> { Name("") }
        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    @DisplayName("공백 문자로만 구성된 이름은 거부된다")
    @Test
    fun throwsBadRequest_whenNameIsWhitespace() {
        val ex = assertThrows<CoreException> { Name("   ") }
        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    // AC-20
    @DisplayName("두 글자 이상의 이름은 마지막 한 글자를 *로 마스킹한다")
    @Test
    fun masksLastCharacter() {
        assertThat(Name("홍길동").masked()).isEqualTo("홍길*")
    }

    // AC-21
    @DisplayName("한 글자 이름은 글자 전체를 *로 마스킹한다")
    @Test
    fun masksSingleCharacter() {
        assertThat(Name("이").masked()).isEqualTo("*")
    }

    @DisplayName("영문 이름도 마지막 한 글자를 *로 마스킹한다")
    @Test
    fun masksEnglishName() {
        assertThat(Name("John").masked()).isEqualTo("Joh*")
    }
}
