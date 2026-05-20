package com.loopers.domain.user.vo

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/**
 * 
 * @since   2026. 5. 20.
 * @author  hyunjikoh
 */
@Embeddable
data class PhoneNumber (
    @Column(name = "phone_number", nullable = false, length = 13)
    val value: String
){
    init{
        if(!PATTERN.matches(value)){
            throw CoreException(ErrorType.BAD_REQUEST ,
                "휴대폰 번호는 010-XXXX-XXXX 형식이어야 합니다.",)
        }
    }

    /**
     * 가운데 4자리를 `****`로 치환한다. 예: `010-1234-5678` → `010-****-5678`.
     */
    fun masked(): String = value.replace(PATTERN, "$1-****-$2")

    companion object {
        private val PATTERN = Regex("^(010)-\\d{4}-(\\d{4})$")
    }
}
