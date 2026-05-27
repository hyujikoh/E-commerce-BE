package com.loopers.domain.user.vo

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

/**
 * 평문 비밀번호 + 비밀번호 정책 검증을 캡슐화한다.
 * encoder 의존이 없으며, 해싱은 [Password.encode]에서 수행한다.
 */
class RawPassword(
    val plainText: String,
    birthDate: BirthDate,
) {
    init {
        if (!ALLOWED_PATTERN.matches(plainText)) {
            throw CoreException(
                ErrorType.BAD_REQUEST,
                "비밀번호는 8~16자의 영문/숫자/특수문자만 사용할 수 있습니다.",
            )
        }
        val lowered = plainText.lowercase()
        if (lowered.contains(birthDate.toYyyyMmDd()) || lowered.contains(birthDate.toYyyyDashMmDashDd())) {
            throw CoreException(
                ErrorType.BAD_REQUEST,
                "비밀번호에 생년월일을 포함할 수 없습니다.",
            )
        }
    }

    companion object {
        private val ALLOWED_PATTERN = Regex("^[A-Za-z0-9\\p{Punct}]{8,16}$")
    }
}
