package com.loopers.domain.payment

/**
 * PG 연동 포트. 구현은 infrastructure 계층(FeignClient + CircuitBreaker).
 *
 * 모든 메서드는 외부 HTTP 호출이므로 **트랜잭션 밖에서 호출**한다 — 외부 지연이 DB 커넥션·행 잠금을
 * 붙들지 않게 하기 위함이다. 호출 결과의 DB 반영은 별도 트랜잭션(PaymentService)에서 수행한다.
 */
interface PgPaymentGateway {
    /**
     * PG 에 결제 생성을 요청한다. 예외를 던지지 않고 항상 [PgRequestResult] 로 결과를 돌려준다
     * (요청 실패가 내부 장애로 전파되지 않아야 한다 — Fallback 요구사항).
     */
    fun requestPayment(request: PgPaymentRequest): PgRequestResult

    /** transactionKey 로 단건 상태를 조회한다. 조회 실패(장애 포함) 시 null. */
    fun findTransaction(guestId: Long, transactionKey: String): PgTransaction?

    /**
     * 주문 ID 에 매인 거래 목록을 조회한다. 접수 불명(타임아웃) 건의 사후 확인용.
     *
     * 반환의 3분법 — 호출자(상태 동기화)가 "실패 확정" 여부를 가르는 기준이므로 구분이 중요하다:
     * - 목록: 거래 존재 확정
     * - 빈 목록: 거래 없음 확정(PG 가 404 로 응답) — 접수 자체가 안 됐다
     * - null: 조회 실패(장애·타임아웃) — 알 수 없음. 실패 확정하면 안 된다
     */
    fun findTransactionsByOrderId(guestId: Long, orderId: String): List<PgTransaction>?
}

data class PgPaymentRequest(
    val guestId: Long,
    val orderId: String,
    val cardType: CardType,
    val cardNo: String,
    val amount: Long,
)

/**
 * 결제 생성 요청의 3분법 결과. "실패"를 확실성에 따라 둘로 나눈다 —
 * PG 에 거래가 생겼는지 모르는 실패([Unknown])는 함부로 실패 확정하면 안 되기 때문이다.
 */
sealed interface PgRequestResult {
    /** PG 가 접수했다 — transactionKey 확보, 비동기 처리 결과 대기. */
    data class Accepted(val transactionKey: String) : PgRequestResult

    /** PG 가 확정적으로 거절했거나(에러 응답) 서킷 오픈으로 호출 자체를 안 했다 — PG 에 거래 없음 확실. */
    data class Rejected(val reason: String) : PgRequestResult

    /** 타임아웃 등 응답 유실 — PG 에 거래가 생성됐는지 알 수 없다. 상태 동기화로 사후 확정한다. */
    data class Unknown(val reason: String) : PgRequestResult
}

/** PG 가 보고하는 거래 상태 스냅샷. */
data class PgTransaction(
    val transactionKey: String,
    val orderId: String,
    val status: PgTransactionStatus,
    val reason: String?,
)

enum class PgTransactionStatus {
    PENDING,
    SUCCESS,
    FAILED,
}
