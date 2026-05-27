package com.loopers.domain.user

import com.loopers.domain.user.vo.BirthDate
import com.loopers.domain.user.vo.Name
import com.loopers.domain.user.vo.Password
import com.loopers.domain.user.vo.PhoneNumber
import com.loopers.domain.user.vo.RawPassword
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    private val log = LoggerFactory.getLogger(UserService::class.java)

    @Transactional
    fun signUp(
        loginId: String,
        rawPassword: RawPassword,
        name: Name,
        birthDate: BirthDate,
        email: String,
        phoneNumber: PhoneNumber,
    ): UserModel {
        val user = UserModel(
            loginId = loginId,
            password = Password.encode(rawPassword, passwordEncoder),
            name = name,
            birthDate = birthDate,
            email = email,
            phoneNumber = phoneNumber,
        )
        return userRepository.save(user)
    }

    @Transactional(readOnly = true)
    fun findByLoginId(loginId: String): UserModel {
        return userRepository.findByLoginId(loginId)
            ?: run {
                log.debug("회원을 찾을 수 없습니다. loginId={}", loginId)
                throw CoreException(
                    errorType = ErrorType.NOT_FOUND,
                    customMessage = "회원을 찾을 수 없습니다.",
                )
            }
    }

    @Transactional
    fun changePassword(loginId: String, currentPlainPassword: String, newPlainPassword: String) {
        val user = findByLoginId(loginId)
        val newRawPassword = RawPassword(newPlainPassword, user.birthDate)
        user.changePassword(newRawPassword, currentPlainPassword, passwordEncoder)
    }
}
