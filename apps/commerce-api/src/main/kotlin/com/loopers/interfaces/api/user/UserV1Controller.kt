package com.loopers.interfaces.api.user

import com.loopers.domain.user.UserService
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.user.auth.AuthenticatedUser
import com.loopers.interfaces.api.user.auth.LoopersAuth
import com.loopers.interfaces.api.user.auth.LoopersAuthArgumentResolver.Companion.HEADER_LOGIN_PW
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserV1Controller(
    private val userService: UserService,
) : UserV1ApiSpec {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    override fun signUp(
        @Valid @RequestBody request: UserV1Dto.SignUpRequest,
    ): ApiResponse<UserV1Dto.SignUpResponse> {
        val user = userService.signUp(
            loginId = request.loginId,
            rawPassword = request.toRawPassword(),
            name = request.toName(),
            birthDate = request.toBirthDate(),
            email = request.email,
            phoneNumber = request.toPhoneNumber(),
        )
        return ApiResponse.success(UserV1Dto.SignUpResponse.from(user))
    }

    @GetMapping("/me")
    override fun getMyInfo(
        @LoopersAuth authenticatedUser: AuthenticatedUser,
    ): ApiResponse<UserV1Dto.UserInfoResponse> {
        val user = userService.findByLoginId(authenticatedUser.loginId)
        return ApiResponse.success(UserV1Dto.UserInfoResponse.from(user))
    }

    @PatchMapping("/me/password")
    override fun changePassword(
        @LoopersAuth authenticatedUser: AuthenticatedUser,
        @RequestHeader(HEADER_LOGIN_PW) currentPlainPassword: String,
        @Valid @RequestBody request: UserV1Dto.ChangePasswordRequest,
    ): ApiResponse<Unit> {
        userService.changePassword(
            loginId = authenticatedUser.loginId,
            currentPlainPassword = currentPlainPassword,
            newPlainPassword = request.newPassword,
        )
        return ApiResponse.success<Unit>(null)
    }
}
