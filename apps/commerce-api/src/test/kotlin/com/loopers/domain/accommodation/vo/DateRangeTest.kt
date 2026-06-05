package com.loopers.domain.accommodation.vo

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class DateRangeTest {
    @DisplayName("DateRange 를 생성할 때,")
    @Nested
    inner class Create {
        @DisplayName("체크아웃이 체크인보다 이후가 아니면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenCheckOutIsNotAfterCheckIn() {
            val sameDay = LocalDate.of(2026, 6, 10)

            val exception = assertThrows<CoreException> { DateRange(sameDay, sameDay) }

            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("투숙 일자를 계산할 때,")
    @Nested
    inner class Dates {
        @DisplayName("nights 는 체크인~체크아웃의 일수이고, datesExclusive 는 체크아웃 당일을 제외한다.")
        @Test
        fun computesNightsAndExclusiveDates() {
            val range = DateRange(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 13))

            assertThat(range.nights).isEqualTo(3)
            assertThat(range.datesExclusive()).containsExactly(
                LocalDate.of(2026, 6, 10),
                LocalDate.of(2026, 6, 11),
                LocalDate.of(2026, 6, 12),
            )
        }
    }
}
