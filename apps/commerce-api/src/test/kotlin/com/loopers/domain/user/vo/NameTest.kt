package com.loopers.domain.user.vo

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NameTest {
    @DisplayName("빈 이름은 거부된다 (AC-8).")
    @Test
    fun throwsBadRequest_whenNameIsBlank() {
        val ex = assertThrows<CoreException> { Name("") }
        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    @DisplayName("공백만으로 구성된 이름은 거부된다.")
    @Test
    fun throwsBadRequest_whenNameIsWhitespace() {
        val ex = assertThrows<CoreException> { Name("   ") }
        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    @DisplayName("이름 마지막 글자를 *로 치환한다 (AC-20).")
    @Test
    fun masksLastCharacter() {
        assertThat(Name("홍길동").masked()).isEqualTo("홍길*")
    }

    @DisplayName("1글자 이름은 전체를 *로 마스킹한다 (AC-21).")
    @Test
    fun masksSingleCharacter() {
        assertThat(Name("이").masked()).isEqualTo("*")
    }

    @DisplayName("영문 이름도 마지막 글자가 *로 치환된다.")
    @Test
    fun masksEnglishName() {
        assertThat(Name("John").masked()).isEqualTo("Joh*")
    }
}
