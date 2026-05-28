package com.loopers.domain.user.vo

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class Name(
    @Column(name = "name", nullable = false)
    val value: String,
) {
    init {
        if (value.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.")
        }
    }

    /**
     * 마지막 글자 1자를 `*`로 치환한다. 1글자도 동일하게 적용해 `*`만 반환한다.
     */
    fun masked(): String {
        if (value.length <= 1) return "*"
        return value.dropLast(1) + "*"
    }
}
