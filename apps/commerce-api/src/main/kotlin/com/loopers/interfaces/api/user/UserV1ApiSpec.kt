package com.loopers.interfaces.api.user

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.user.auth.AuthenticatedUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "User V1 API", description = "Loopers 회원 API 입니다.")
interface UserV1ApiSpec {
    @Operation(summary = "회원 가입", description = "신규 회원을 등록합니다.")
    fun signUp(request: UserV1Dto.SignUpRequest): ApiResponse<UserV1Dto.SignUpResponse>

    @Operation(summary = "내 정보 조회", description = "인증 헤더로 본인 정보를 조회합니다.")
    fun getMyInfo(authenticatedUser: AuthenticatedUser): ApiResponse<UserV1Dto.UserInfoResponse>

    @Operation(
        summary = "비밀번호 수정",
        description = "인증된 사용자의 비밀번호를 새 비밀번호로 변경합니다. 헤더의 현재 비밀번호로 인증한 뒤, 본문의 newPassword가 정책을 통과해야 합니다.",
    )
    fun changePassword(
        authenticatedUser: AuthenticatedUser,
        currentPlainPassword: String,
        request: UserV1Dto.ChangePasswordRequest,
    ): ApiResponse<Unit>
}
