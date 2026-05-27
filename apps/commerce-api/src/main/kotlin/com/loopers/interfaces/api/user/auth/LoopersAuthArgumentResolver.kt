package com.loopers.interfaces.api.user.auth

import com.loopers.application.user.AuthFacade
import com.loopers.domain.user.UserModel
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

@Component
class LoopersAuthArgumentResolver(
    private val authFacade: AuthFacade,
) : HandlerMethodArgumentResolver {
    companion object {
        const val HEADER_LOGIN_ID = "X-Loopers-LoginId"
        const val HEADER_LOGIN_PW = "X-Loopers-LoginPw"
    }

    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return parameter.hasParameterAnnotation(LoopersAuth::class.java) &&
            parameter.parameterType == AuthenticatedUser::class.java
    }

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any {
        val loginId = webRequest.getHeader(HEADER_LOGIN_ID)
        val loginPw = webRequest.getHeader(HEADER_LOGIN_PW)
        if (loginId.isNullOrBlank() || loginPw.isNullOrBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "인증 헤더가 누락되었습니다.")
        }
        if (!UserModel.LOGIN_ID_PATTERN.matches(loginId)) {
            throw CoreException(ErrorType.BAD_REQUEST, "인증 헤더가 올바르지 않습니다.")
        }
        return authFacade.authenticate(loginId, loginPw)
    }
}
