package com.loopers.domain.accommodation.vo

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MoneyTest {
    @DisplayName("Money 를 생성할 때,")
    @Nested
    inner class Create {
        @DisplayName("금액이 음수면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenAmountIsNegative() {
            val exception = assertThrows<CoreException> { Money.krw(-1) }

            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("통화 코드가 3자리가 아니면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenCurrencyIsNotThreeLetters() {
            val exception = assertThrows<CoreException> { Money(1_000, "WONS") }
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)

            val exception2 = assertThrows<CoreException> { Money(1_000, "KR") }
            assertThat(exception2.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("Money 를 더할 때,")
    @Nested
    inner class Plus {
        @DisplayName("같은 통화면, 금액을 합산한다.")
        @Test
        fun sumsAmount_whenSameCurrency() {
            val result = Money.krw(10_000) + Money.krw(20_000)

            assertThat(result).isEqualTo(Money.krw(30_000))
        }

        @DisplayName("통화가 다르면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenDifferentCurrency() {
            val exception = assertThrows<CoreException> { Money.krw(10_000) + Money(10_000, "USD") }

            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
