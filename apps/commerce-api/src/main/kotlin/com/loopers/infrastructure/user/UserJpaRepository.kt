package com.loopers.infrastructure.user

import com.loopers.domain.user.UserModel
import org.springframework.data.jpa.repository.JpaRepository

interface UserJpaRepository : JpaRepository<UserModel, Long> {
    fun findByLoginId(loginId: String): UserModel?
}
