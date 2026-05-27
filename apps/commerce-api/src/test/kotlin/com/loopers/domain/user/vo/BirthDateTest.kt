package com.loopers.domain.user.vo

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BirthDateTest {
    @DisplayName("yyyyMMdd 포맷으로 변환한다.")
    @Test
    fun formatsAsYyyyMmDd() {
        val birthDate = BirthDate(LocalDate.of(1990, 1, 15))

        assertThat(birthDate.toYyyyMmDd()).isEqualTo("19900115")
    }

    @DisplayName("yyyy-MM-dd 포맷으로 변환한다.")
    @Test
    fun formatsAsYyyyDashMmDashDd() {
        val birthDate = BirthDate(LocalDate.of(1990, 1, 15))

        assertThat(birthDate.toYyyyDashMmDashDd()).isEqualTo("1990-01-15")
    }
}
