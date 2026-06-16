package com.loopers.support.error

import org.springframework.http.HttpStatus

enum class ErrorType(val status: HttpStatus, val code: String, val message: String) {
    /** 범용 에러 */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR.reasonPhrase, "일시적인 오류가 발생했습니다."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.reasonPhrase, "잘못된 요청입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.reasonPhrase, "존재하지 않는 요청입니다."),
    CONFLICT(HttpStatus.CONFLICT, HttpStatus.CONFLICT.reasonPhrase, "이미 존재하는 리소스입니다."),

    /** 회원 / 인증 */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증에 실패했습니다."),
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "DUPLICATE_LOGIN_ID", "이미 사용 중인 로그인 ID입니다."),

    /** 숙박 예약 */
    OUT_OF_INVENTORY(HttpStatus.CONFLICT, "OUT_OF_INVENTORY", "선택한 기간에 잔여 객실이 없습니다."),
    RATE_NOT_FOUND(HttpStatus.NOT_FOUND, "RATE_NOT_FOUND", "요청한 기간의 요금 정보를 찾을 수 없습니다."),
    INVALID_RESERVATION_STATE(HttpStatus.CONFLICT, "INVALID_RESERVATION_STATE", "현재 예약 상태에서는 수행할 수 없는 작업입니다."),
}
