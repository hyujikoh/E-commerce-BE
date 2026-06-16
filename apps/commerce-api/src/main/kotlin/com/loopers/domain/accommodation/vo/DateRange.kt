package com.loopers.domain.accommodation.vo

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 투숙 기간 Value Object. 체크인(포함) ~ 체크아웃(제외).
 * 재고 차감·요금 합산의 기준이 되는 "투숙 일자"는 체크아웃 당일을 제외한다.
 */
@Embeddable
data class DateRange(
    @Column(name = "check_in_date", nullable = false)
    val checkIn: LocalDate,
    @Column(name = "check_out_date", nullable = false)
    val checkOut: LocalDate,
) {
    init {
        if (!checkOut.isAfter(checkIn)) {
            throw CoreException(ErrorType.BAD_REQUEST, "체크아웃 날짜는 체크인 날짜 이후여야 합니다.")
        }
    }

    /** 숙박 일수 (= 투숙 일자 개수). */
    val nights: Int
        get() = ChronoUnit.DAYS.between(checkIn, checkOut).toInt()

    /** 투숙 일자 목록 (체크아웃 당일 제외). 재고 차감·요금 스냅샷의 기준. */
    fun datesExclusive(): List<LocalDate> =
        (0 until nights).map { checkIn.plusDays(it.toLong()) }
}
