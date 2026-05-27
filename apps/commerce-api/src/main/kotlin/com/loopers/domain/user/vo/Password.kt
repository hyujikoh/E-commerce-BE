package com.loopers.domain.user.vo

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * BCrypt 해시 래퍼. 응답 직렬화 시 노출을 방지한다(NFR-3).
 */
@Embeddable
data class Password(
    @get:JsonIgnore
    @Column(name = "password_hash", nullable = false, length = 60)
    val hashedValue: String,
) {
    fun matches(plainText: String, encoder: PasswordEncoder): Boolean =
        encoder.matches(plainText, hashedValue)

    companion object {
        fun fromHash(hashedValue: String): Password = Password(hashedValue)

        fun encode(raw: RawPassword, encoder: PasswordEncoder): Password =
            Password(encoder.encode(raw.plainText))
    }
}
