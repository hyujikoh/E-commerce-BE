package com.loopers.domain.user.vo

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 
 * @since   2026. 5. 18.
 * @author  hyunjikoh
 */
@Embeddable
data class BirthDate(
    @Column(name = "birth_date", nullable = false)
    val value: LocalDate,
){
    fun toYyyyMmDd(): String = value.format(YYYY_MM_DD)
    fun toYyyyDashMmDashDd(): String = value.format(YYYY_DASH_MM_DASH_DD)


    companion object{
        private val YYYY_MM_DD = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val YYYY_DASH_MM_DASH_DD = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}
