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
    PROPERTY_NOT_FOUND(HttpStatus.NOT_FOUND, "PROPERTY_NOT_FOUND", "존재하지 않는 숙소입니다."),
    OUT_OF_INVENTORY(HttpStatus.CONFLICT, "OUT_OF_INVENTORY", "선택한 기간에 잔여 객실이 없습니다."),
    RATE_NOT_FOUND(HttpStatus.NOT_FOUND, "RATE_NOT_FOUND", "요청한 기간의 요금 정보를 찾을 수 없습니다."),
    INVALID_RESERVATION_STATE(HttpStatus.CONFLICT, "INVALID_RESERVATION_STATE", "현재 예약 상태에서는 수행할 수 없는 작업입니다."),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", "존재하지 않는 예약입니다."),
    RESERVATION_NOT_OWNED(HttpStatus.FORBIDDEN, "RESERVATION_NOT_OWNED", "본인의 예약이 아닙니다."),

    /** 결제 */
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "존재하지 않는 결제입니다."),
    INVALID_PAYMENT_STATE(HttpStatus.CONFLICT, "INVALID_PAYMENT_STATE", "현재 결제 상태에서는 수행할 수 없는 작업입니다."),

    /** 쿠폰 */
    INVALID_COUPON(HttpStatus.BAD_REQUEST, "INVALID_COUPON", "쿠폰 정보가 올바르지 않습니다."),
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND", "존재하지 않는 쿠폰입니다."),
    COUPON_NOT_OWNED(HttpStatus.FORBIDDEN, "COUPON_NOT_OWNED", "본인이 소유한 쿠폰이 아닙니다."),
    COUPON_ALREADY_USED(HttpStatus.CONFLICT, "COUPON_ALREADY_USED", "이미 사용된 쿠폰입니다."),
    COUPON_EXPIRED(HttpStatus.CONFLICT, "COUPON_EXPIRED", "만료된 쿠폰입니다."),
    COUPON_MIN_ORDER_NOT_MET(HttpStatus.BAD_REQUEST, "COUPON_MIN_ORDER_NOT_MET", "쿠폰 최소 주문 금액 조건을 충족하지 않습니다."),
    COUPON_ALREADY_ISSUED(HttpStatus.CONFLICT, "COUPON_ALREADY_ISSUED", "이미 발급받은 쿠폰입니다."),
}
