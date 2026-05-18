package com.loopers.domain.user.vo

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * 
 * @since   2026. 5. 18.
 * @author  hyunjikoh
 */
class BirthDateTest {
    @Test
    @DisplayName("yyyyMMdd 포맷으로 변환한다.")
    fun toYyyyDashMmDashDd() {
        val birthDate = BirthDate(LocalDate.of(2026,1, 15));
        assertThat(birthDate.toYyyyMmDd()).isEqualTo("20260115")
    }

    @Test
    @DisplayName("yyyy-MM-dd 포맷으로 변환한다.")

    fun formatsAsYyyyDashMmDashDd() {
        val birthDate = BirthDate(LocalDate.of(2026,1, 15));
        assertThat(birthDate.toYyyyDashMmDashDd()).isEqualTo("2026-01-15")

    }


}
