package com.loopers.application.user

import com.loopers.domain.user.UserService
import com.loopers.interfaces.api.user.auth.AuthenticatedUser
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class AuthFacade(
    private val userService: UserService,
    private val passwordEncoder: PasswordEncoder,
) {
    /**
     * loginId / 평문 비밀번호로 인증한다.
     * - ID 미존재 또는 비밀번호 불일치 → UNAUTHORIZED (정보 추론 차단, NFR-2)
     */
    @Transactional(readOnly = true)
    fun authenticate(loginId: String, plainPassword: String): AuthenticatedUser {
        val user = try {
            userService.findByLoginId(loginId)
        } catch (e: CoreException) {
            if (e.errorType == ErrorType.NOT_FOUND) throw CoreException(ErrorType.UNAUTHORIZED) else throw e
        }
        if (!user.password.matches(plainPassword, passwordEncoder)) {
            throw CoreException(ErrorType.UNAUTHORIZED)
        }
        return AuthenticatedUser(loginId = user.loginId)
    }
}
