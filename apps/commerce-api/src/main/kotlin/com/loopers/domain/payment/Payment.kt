package com.loopers.domain.payment

import com.loopers.domain.BaseEntity
import com.loopers.domain.accommodation.Reservation
import com.loopers.domain.accommodation.vo.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Embedded
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

/**
 * 결제 애그리거트 루트. 예약(Reservation)과는 ID 로만 참조한다.
 *
 * - 결제 금액은 클라이언트 입력이 아니라 예약의 최종 금액 스냅샷([Reservation.finalAmount])으로 서버가 확정한다.
 * - orderId 는 예약 ID 를 PG 규격(6자리 이상)으로 패딩한 값 — 예약당 결제 요청의 멱등 키다.
 * - 상태 전이는 이 루트의 메서드로만 일어나며, 각 메서드가 전이 조건을 자체 검증한다.
 *   중복 콜백/동시 폴링에 대한 멱등 처리(종결 상태 no-op)는 PaymentService 가 행 잠금과 함께 담당한다.
 */
@Entity
@Table(name = "payment")
class Payment private constructor(
    reservationId: Long,
    guestId: Long,
    orderId: String,
    cardType: CardType,
    cardNo: String,
    amount: Money,
) : BaseEntity() {

    @Column(name = "reservation_id", nullable = false)
    var reservationId: Long = reservationId
        protected set

    /** PG 요청 헤더(X-USER-ID)와 사후 상태 조회에 쓰는 게스트 ID. */
    @Column(name = "guest_id", nullable = false)
    var guestId: Long = guestId
        protected set

    @Column(name = "order_id", nullable = false, length = 20)
    var orderId: String = orderId
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, length = 20)
    var cardType: CardType = cardType
        protected set

    @Column(name = "card_no", nullable = false, length = 19)
    var cardNo: String = cardNo
        protected set

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "amount", nullable = false))
    @AttributeOverride(name = "currency", column = Column(name = "currency", nullable = false, length = 3))
    var amount: Money = amount
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: PaymentStatus = PaymentStatus.CREATED
        protected set

    /** PG 트랜잭션 키. 접수 확인(REQUESTED) 또는 결과 반영 시점에 채워진다. */
    @Column(name = "transaction_key", length = 100)
    var transactionKey: String? = null
        protected set

    /** 실패 사유(REQUEST_FAILED/FAILED 에서만). PG 가 준 사유 또는 요청 실패 원인. */
    @Column(name = "failure_reason", length = 500)
    var failureReason: String? = null
        protected set

    // ── 상태 전이 ──

    /** PG 접수 성공 — transactionKey 를 확보하고 처리 결과를 기다린다. */
    fun markRequested(transactionKey: String) {
        transitionTo(PaymentStatus.REQUESTED, allowedFrom = setOf(PaymentStatus.CREATED))
        this.transactionKey = transactionKey
    }

    /** PG 접수 확정 실패 — PG 에 거래가 생성되지 않았음이 확실한 경우만(에러 응답·서킷 오픈). */
    fun markRequestFailed(reason: String) {
        transitionTo(PaymentStatus.REQUEST_FAILED, allowedFrom = setOf(PaymentStatus.CREATED))
        this.failureReason = reason
    }

    /** 결제 성공 확정. 접수 응답 유실(타임아웃) 후 폴링으로 결과를 먼저 알 수 있어 CREATED 에서도 허용한다. */
    fun succeed(transactionKey: String) {
        transitionTo(PaymentStatus.SUCCESS, allowedFrom = setOf(PaymentStatus.CREATED, PaymentStatus.REQUESTED))
        this.transactionKey = transactionKey
    }

    /** PG 처리 실패(한도 초과·카드 오류 등). [succeed] 와 같은 이유로 CREATED 에서도 허용한다. */
    fun fail(transactionKey: String, reason: String?) {
        transitionTo(PaymentStatus.FAILED, allowedFrom = setOf(PaymentStatus.CREATED, PaymentStatus.REQUESTED))
        this.transactionKey = transactionKey
        this.failureReason = reason
    }

    private fun transitionTo(target: PaymentStatus, allowedFrom: Set<PaymentStatus>) {
        if (status !in allowedFrom) {
            throw CoreException(
                ErrorType.INVALID_PAYMENT_STATE,
                "현재 상태($status)에서 $target(으)로 전이할 수 없습니다.",
            )
        }
        status = target
    }

    companion object {
        private val REGEX_CARD_NO = Regex("^\\d{4}-\\d{4}-\\d{4}-\\d{4}$")

        /** 예약 ID → PG 주문 ID. PG 는 6자리 이상 문자열을 요구한다. */
        fun orderIdOf(reservationId: Long): String = "%06d".format(reservationId)

        /** PG 주문 ID → 예약 ID(콜백·폴링의 역추적용). */
        fun reservationIdOf(orderId: String): Long = orderId.toLong()

        fun create(reservation: Reservation, cardType: CardType, cardNo: String): Payment {
            if (!REGEX_CARD_NO.matches(cardNo)) {
                throw CoreException(ErrorType.BAD_REQUEST, "카드 번호는 xxxx-xxxx-xxxx-xxxx 형식이어야 합니다.")
            }
            return Payment(
                reservationId = reservation.id,
                guestId = reservation.guestId,
                orderId = orderIdOf(reservation.id),
                cardType = cardType,
                cardNo = cardNo,
                amount = reservation.finalAmount,
            )
        }
    }
}
