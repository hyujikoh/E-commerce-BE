package com.loopers.infrastructure.user

import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserRepository
import org.springframework.stereotype.Component

@Component
class UserRepositoryImpl(
    private val userJpaRepository: UserJpaRepository,
) : UserRepository {
    override fun save(user: UserModel): UserModel = userJpaRepository.save(user)

    override fun findByLoginId(loginId: String): UserModel? = userJpaRepository.findByLoginId(loginId)
}
