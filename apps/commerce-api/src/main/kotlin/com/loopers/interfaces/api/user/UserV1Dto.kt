package com.loopers.interfaces.api.user

import com.loopers.domain.user.UserModel
import com.loopers.domain.user.vo.BirthDate
import com.loopers.domain.user.vo.Name
import com.loopers.domain.user.vo.PhoneNumber
import com.loopers.domain.user.vo.RawPassword
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import java.time.LocalDate

class UserV1Dto {
    data class SignUpRequest(
        @field:NotBlank(message = "로그인 ID는 필수입니다.")
        @field:Pattern(regexp = "^[A-Za-z0-9]+$", message = "로그인 ID는 영문/숫자만 사용할 수 있습니다.")
        val loginId: String,
        @field:NotBlank(message = "비밀번호는 필수입니다.")
        val password: String,
        @field:NotBlank(message = "이름은 필수입니다.")
        val name: String,
        @field:NotNull(message = "생년월일은 필수입니다.")
        val birthDate: LocalDate,
        @field:NotBlank(message = "이메일은 필수입니다.")
        @field:Email(message = "이메일 형식이 올바르지 않습니다.")
        val email: String,
        @field:NotBlank(message = "휴대폰 번호는 필수입니다.")
        @field:Pattern(regexp = "^010-\\d{4}-\\d{4}$", message = "휴대폰 번호는 010-XXXX-XXXX 형식이어야 합니다.")
        val phoneNumber: String,
    ) {
        fun toBirthDate(): BirthDate = BirthDate(birthDate)

        fun toRawPassword(): RawPassword = RawPassword(password, toBirthDate())

        fun toName(): Name = Name(name)

        fun toPhoneNumber(): PhoneNumber = PhoneNumber(phoneNumber)
    }

    data class SignUpResponse(
        val loginId: String,
    ) {
        companion object {
            fun from(user: UserModel): SignUpResponse = SignUpResponse(loginId = user.loginId)
        }
    }

    data class ChangePasswordRequest(
        @field:NotBlank(message = "새 비밀번호는 필수입니다.")
        val newPassword: String,
    )

    data class UserInfoResponse(
        val loginId: String,
        val name: String,
        val birthDate: LocalDate,
        val email: String,
        val phoneNumber: String,
    ) {
        companion object {
            fun from(user: UserModel): UserInfoResponse = UserInfoResponse(
                loginId = user.loginId,
                name = user.name.masked(),
                birthDate = user.birthDate.value,
                email = user.email,
                phoneNumber = user.phoneNumber.masked(),
            )
        }
    }
}
