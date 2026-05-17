package com.loopers.domain.user

import com.loopers.domain.BaseEntity
import com.loopers.domain.user.vo.BirthDate
import com.loopers.domain.user.vo.Name
import com.loopers.domain.user.vo.Password
import com.loopers.domain.user.vo.PhoneNumber
import com.loopers.domain.user.vo.RawPassword
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.springframework.security.crypto.password.PasswordEncoder

@Entity
@Table(
    name = "users",
    uniqueConstraints = [UniqueConstraint(name = "uk_users_login_id", columnNames = ["login_id"])],
)
class UserModel(
    loginId: String,
    password: Password,
    name: Name,
    birthDate: BirthDate,
    email: String,
    phoneNumber: PhoneNumber,
) : BaseEntity() {
    @Column(name = "login_id", nullable = false, length = 50)
    var loginId: String = loginId
        protected set

    @Embedded
    var password: Password = password
        protected set

    @Embedded
    var name: Name = name
        protected set

    @Embedded
    var birthDate: BirthDate = birthDate
        protected set

    @Column(name = "email", nullable = false, length = 255)
    var email: String = email
        protected set

    @Embedded
    var phoneNumber: PhoneNumber = phoneNumber
        protected set

    init {
        if (loginId.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 비어있을 수 없습니다.")
        }
        if (!LOGIN_ID_PATTERN.matches(loginId)) {
            throw CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문/숫자만 사용할 수 있습니다.")
        }
        if (email.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "이메일은 비어있을 수 없습니다.")
        }
    }

    /**
     * 비밀번호를 변경한다.
     * - 새 비밀번호가 현재와 동일 → BAD_REQUEST (AC-33)
     *
     * 현재 비밀번호 인증(BCrypt 매칭)은 호출 이전 단계에서 수행되어야 한다.
     * (예: @LoopersAuth → AuthFacade.authenticate)
     */
    fun changePassword(
        newRawPassword: RawPassword,
        currentPlainPassword: String,
        encoder: PasswordEncoder,
    ) {
        if (newRawPassword.plainText == currentPlainPassword) {
            throw CoreException(
                ErrorType.BAD_REQUEST,
                "새 비밀번호는 현재 비밀번호와 같을 수 없습니다.",
            )
        }
        this.password = Password.encode(newRawPassword, encoder)
    }

    companion object {
        val LOGIN_ID_PATTERN = Regex("^[A-Za-z0-9]+$")
    }
}
