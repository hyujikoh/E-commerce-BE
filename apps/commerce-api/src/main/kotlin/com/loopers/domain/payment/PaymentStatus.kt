package com.loopers.domain.payment

/**
 * 결제 상태. PG 응답의 확실성에 따라 상태를 구분한다.
 *
 * - PG 가 에러 응답을 준 경우: 거래가 생성되지 않았음이 확실하다 → [REQUEST_FAILED]
 * - 타임아웃 등 응답을 받지 못한 경우: 거래 생성 여부를 알 수 없다 → [CREATED] 에 머물고
 *   상태 동기화(스케줄러)가 PG 상태 확인 API 로 사후 확정한다.
 */
enum class PaymentStatus {
    /** 결제 레코드 생성 — PG 접수 결과 대기. 타임아웃 등 접수 불명 상태도 여기 머문다(동기화 대상). */
    CREATED,

    /** PG 접수 성공(transactionKey 확보) — 처리 결과 대기. 콜백 유실 시 동기화 대상. */
    REQUESTED,

    /** 결제 성공(종결). */
    SUCCESS,

    /** PG 처리 실패 — 한도 초과/카드 오류 등(종결). */
    FAILED,

    /** PG 접수 확정 실패 — PG 에 거래가 생성되지 않음(종결). 사용자 재시도 가능. */
    REQUEST_FAILED,
    ;

    /** 더 이상 상태가 바뀌지 않는 종결 상태인지. */
    val isTerminal: Boolean
        get() = this == SUCCESS || this == FAILED || this == REQUEST_FAILED

    /** 이 상태의 결제가 있어도 같은 예약에 새 결제를 만들 수 있는지(실패 건만 재시도 허용). */
    val allowsRetry: Boolean
        get() = this == FAILED || this == REQUEST_FAILED
}
