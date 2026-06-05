package com.loopers.domain.accommodation.vo

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/**
 * 금액 Value Object. 통화를 함께 보존하기 위해 amount + currency 두 컬럼으로 매핑한다.
 * (단일 컬럼 매핑은 통화 정보를 잃으므로 피한다.)
 *
 * amount 는 원화 정수(최소 단위, KRW) 가정. 다통화·소수 통화가 필요해지면 BigDecimal + scale 로 확장한다.
 */
@Embeddable
data class Money(
    @Column(name = "amount", nullable = false)
    val amount: Long,
    @Column(name = "currency", nullable = false, length = 3)
    val currency: String,
) {
    init {
        if (amount < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "금액은 음수일 수 없습니다.")
        }
        if (currency.length != 3) {
            throw CoreException(ErrorType.BAD_REQUEST, "통화는 3자리 ISO 4217 코드여야 합니다.")
        }
    }

    operator fun plus(other: Money): Money {
        if (currency != other.currency) {
            throw CoreException(ErrorType.BAD_REQUEST, "통화가 다른 금액은 더할 수 없습니다. ($currency, ${other.currency})")
        }
        return copy(amount = amount + other.amount)
    }

    companion object {
        fun krw(amount: Long): Money = Money(amount, "KRW")
    }
}
