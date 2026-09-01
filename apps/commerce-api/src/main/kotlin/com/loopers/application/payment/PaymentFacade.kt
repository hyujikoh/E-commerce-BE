package com.loopers.application.payment

import com.loopers.domain.accommodation.CancelReason
import com.loopers.domain.accommodation.ReservationService
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PgPaymentGateway
import com.loopers.domain.payment.PgPaymentRequest
import com.loopers.domain.payment.PgRequestResult
import com.loopers.domain.payment.PgResultOutcome
import com.loopers.domain.payment.PgTransaction
import com.loopers.domain.payment.PgTransactionStatus
import com.loopers.support.error.CoreException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.ZonedDateTime

/**
 * 결제 유스케이스 오케스트레이션. PG 호출(외부 HTTP)은 트랜잭션 밖(여기)에서 수행하고,
 * 결과의 DB 반영은 PaymentService 의 개별 트랜잭션으로 위임한다 — 외부 지연이
 * DB 커넥션·행 잠금을 붙들지 않게 하기 위함이다.
 */
@Component
class PaymentFacade(
    private val paymentService: PaymentService,
    private val reservationService: ReservationService,
    private val pgPaymentGateway: PgPaymentGateway,
) {
    /**
     * 결제 요청. 결제 레코드 생성(TX) → PG 호출(TX 밖) → 접수 결과 반영(TX).
     *
     * - 멱등: 진행 중/성공 결제가 이미 있으면 PG 를 호출하지 않고 그 결제를 반환한다.
     * - Fallback: PG 실패는 예외가 아니라 상태(REQUEST_FAILED/CREATED)로 담아 정상 응답한다.
     * - 접수 불명(타임아웃)은 CREATED 로 남긴다 — 실패 확정하지 않고 상태 동기화가 사후 확정한다.
     */
    fun pay(reservationId: Long, guestId: Long, cardType: CardType, cardNo: String): PaymentInfo {
        val creation = paymentService.create(reservationId, guestId, cardType, cardNo)
        val payment = creation.payment
        if (!creation.isNew) {
            return PaymentInfo.from(payment)
        }
        val result = pgPaymentGateway.requestPayment(
            PgPaymentRequest(
                guestId = payment.guestId,
                orderId = payment.orderId,
                cardType = payment.cardType,
                cardNo = payment.cardNo,
                amount = payment.amount.amount,
            ),
        )
        return when (result) {
            is PgRequestResult.Accepted -> PaymentInfo.from(paymentService.markRequested(payment.id, result.transactionKey))
            is PgRequestResult.Rejected -> PaymentInfo.from(paymentService.markRequestFailed(payment.id, result.reason))
            is PgRequestResult.Unknown -> PaymentInfo.from(payment)
        }
    }

    /**
     * PG 결제 결과 콜백 처리. 콜백 본문은 위·변조 가능하므로 신뢰하지 않는다 —
     * transactionKey 로 PG 를 재조회해 검증한 스냅샷만 반영한다.
     *
     * 콜백은 best-effort(유실 가능)이고 유실 건은 상태 동기화가 복구하므로,
     * 검증 실패·대상 불일치는 예외 대신 로그만 남기고 조용히 넘어간다(중복·지연 콜백에 멱등).
     */
    fun handleCallback(orderId: String, transactionKey: String) {
        val reservationId = runCatching { Payment.reservationIdOf(orderId) }.getOrElse {
            logger.warn("콜백 orderId 형식 불일치 — 무시. orderId={}", orderId)
            return
        }
        val payment = paymentService.findByReservation(reservationId)
            .firstOrNull { it.transactionKey == transactionKey || (it.transactionKey == null && !it.status.isTerminal) }
        if (payment == null) {
            logger.warn("콜백 대상 결제 없음 — 무시. orderId={}, transactionKey={}", orderId, transactionKey)
            return
        }
        if (payment.status.isTerminal) {
            return
        }
        val verified = pgPaymentGateway.findTransaction(payment.guestId, transactionKey)
        if (verified == null || verified.orderId != payment.orderId) {
            logger.warn(
                "콜백 검증 실패(PG 재조회 불가 또는 orderId 불일치) — 상태 동기화가 사후 처리. paymentId={}, transactionKey={}",
                payment.id,
                transactionKey,
            )
            return
        }
        settle(payment.id, payment.guestId, payment.reservationId, verified)
    }

    /**
     * 검증된 PG 스냅샷을 결제에 반영하고, 이번 반영으로 종결됐을 때만 예약 후속 전이를 수행한다.
     * 콜백과 상태 동기화 스케줄러가 공용으로 쓰는 진입점이다.
     */
    fun settle(paymentId: Long, guestId: Long, reservationId: Long, pgTransaction: PgTransaction) {
        when (paymentService.applyPgResult(paymentId, pgTransaction)) {
            PgResultOutcome.NONE -> Unit
            PgResultOutcome.SUCCEEDED -> confirmReservation(paymentId, reservationId)
            PgResultOutcome.FAILED -> cancelReservation(paymentId, guestId, reservationId)
        }
    }

    /**
     * 결과 미확정(CREATED/REQUESTED) 결제의 상태 동기화. 콜백 유실·접수 불명(타임아웃) 건을
     * PG 조회로 사후 확정한다(스케줄러·수동 복구 공용 진입점).
     *
     * [threshold] 이전에 마지막 갱신된 건만 스캔한다 — 방금 만든 결제는 콜백이 정상 도착할
     * 시간을 준 뒤에 폴링한다. 건별 실패는 로그만 남기고 다음 건을 계속 처리한다.
     *
     * @return 동기화를 시도한 건수
     */
    fun syncPendingResults(threshold: ZonedDateTime = ZonedDateTime.now().minus(SYNC_GRACE)): Int {
        val targets = paymentService.findResultPending(threshold, SYNC_BATCH_SIZE)
        targets.forEach { payment ->
            runCatching { syncOne(payment) }
                .onFailure { e -> logger.error("결제 상태 동기화 실패. paymentId={}", payment.id, e) }
        }
        return targets.size
    }

    private fun syncOne(payment: Payment) {
        val transactionKey = payment.transactionKey
        if (transactionKey != null) {
            // REQUESTED — 접수는 됐고 결과 콜백이 유실된 건. 단건 조회로 결과를 가져온다.
            val transaction = pgPaymentGateway.findTransaction(payment.guestId, transactionKey) ?: return
            settle(payment.id, payment.guestId, payment.reservationId, transaction)
            return
        }
        // CREATED — 접수 자체가 불명(타임아웃)인 건. 주문 ID 로 거래 존재 여부를 확인한다.
        val transactions = pgPaymentGateway.findTransactionsByOrderId(payment.guestId, payment.orderId)
            ?: return // 조회 실패 — 알 수 없으므로 확정하지 않고 다음 회차에 재시도
        // orderId 는 예약 단위라 이전 시도의 거래가 섞일 수 있다 — 다른 결제가 이미 귀속한 키는 제외한다.
        val claimedKeys = paymentService.findByReservation(payment.reservationId)
            .filter { it.id != payment.id }
            .mapNotNull { it.transactionKey }
            .toSet()
        val candidates = transactions.filterNot { it.transactionKey in claimedKeys }
        if (candidates.isEmpty()) {
            // PG 에 이 시도의 거래가 없음 확정 — 접수가 안 됐다. 실패 종결해 재시도를 열어준다.
            paymentService.markRequestFailed(payment.id, "PG 접수가 확인되지 않았습니다. 다시 시도해주세요.")
            return
        }
        val transaction = candidates.firstOrNull { it.status != PgTransactionStatus.PENDING } ?: candidates.first()
        settle(payment.id, payment.guestId, payment.reservationId, transaction)
    }

    /**
     * 결제 성공 → 예약 확정. 결제 SUCCESS 는 이미 커밋된 사실이므로(돈이 빠져나갔다),
     * 예약을 확정할 수 없는 상황(홀드 만료·이미 취소)이어도 결제를 롤백하지 않고
     * 심각 로그로 수동 환불 대상임을 드러낸다.
     */
    private fun confirmReservation(paymentId: Long, reservationId: Long) {
        try {
            if (reservationService.expire(reservationId)) {
                logger.error(
                    "결제는 성공했으나 예약 홀드가 만료되어 취소됨 — 수동 환불 필요. paymentId={}, reservationId={}",
                    paymentId,
                    reservationId,
                )
                return
            }
            reservationService.confirm(reservationId)
        } catch (e: CoreException) {
            logger.error(
                "결제 성공 후 예약 확정 실패 — 수동 조치 필요. paymentId={}, reservationId={}, 사유={}",
                paymentId,
                reservationId,
                e.message,
            )
        }
    }

    /** 결제 실패 → 예약 취소(재고·쿠폰 복구). 이미 종결된 예약이면 로그만 남긴다. */
    private fun cancelReservation(paymentId: Long, guestId: Long, reservationId: Long) {
        try {
            reservationService.cancel(reservationId, guestId, CancelReason.PAYMENT_FAILED)
        } catch (e: CoreException) {
            logger.warn(
                "결제 실패 후 예약 취소 생략(이미 종결된 예약). paymentId={}, reservationId={}, 사유={}",
                paymentId,
                reservationId,
                e.message,
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PaymentFacade::class.java)

        /** 폴링 전 콜백 대기 유예. PG 처리 지연(1~5s)과 콜백 전송 시간을 감안한다. */
        private val SYNC_GRACE: Duration = Duration.ofMinutes(1)
        private const val SYNC_BATCH_SIZE = 100
    }
}
