package com.loopers.domain.accommodation

/** 예약 취소 사유. */
enum class CancelReason {
    /** 사용자 요청 취소 */
    USER_REQUEST,

    /** 소프트 홀드(PENDING) 만료 자동 취소 */
    EXPIRED,

    /** 결제 실패 자동 취소 */
    PAYMENT_FAILED,
}
