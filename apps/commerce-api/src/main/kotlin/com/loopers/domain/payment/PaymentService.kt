package com.loopers.domain.payment

import com.loopers.domain.accommodation.ReservationRepository
import com.loopers.domain.accommodation.ReservationStatus
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

/**
 * 결제 상태를 지휘하는 서비스. PG 호출은 여기 없다 — 외부 호출(Facade, 트랜잭션 밖)과
 * 그 결과의 DB 반영(이 서비스, 트랜잭션 안)을 분리한다.
 *
 * 동시성 가드: 생성은 예약 행 잠금으로, 상태 반영은 결제 행 잠금 + 종결 상태 no-op 으로 직렬화한다.
 * 요청 스레드의 접수 반영 vs 콜백의 결과 반영이 경쟁해도(PG 처리가 접수 응답보다 빨리 끝나는 경우)
 * 늦게 도착한 쪽이 종결 상태를 보고 조용히 물러난다.
 */
@Component
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val reservationRepository: ReservationRepository,
) {
    /**
     * 결제 요청 레코드 생성(CREATED).
     *
     * 예약 행을 FOR UPDATE 로 잠근 뒤 검증(소유·PENDING·미만료)과 중복 가드를 수행한다 —
     * 동일 reservationId 동시 요청은 예약 행 잠금으로 직렬화된다.
     * 이미 진행 중(CREATED/REQUESTED)이거나 성공한 결제가 있으면 새로 만들지 않고 그것을 반환한다(멱등).
     * 실패 건(REQUEST_FAILED/FAILED)만 있으면 다른 카드로 재시도할 수 있으므로 새 결제를 생성한다.
     */
    @Transactional
    fun create(
        reservationId: Long,
        guestId: Long,
        cardType: CardType,
        cardNo: String,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): Payment {
        val reservation = reservationRepository.findWithLock(reservationId)
            ?: throw CoreException(ErrorType.RESERVATION_NOT_FOUND)
        if (reservation.guestId != guestId) {
            throw CoreException(ErrorType.RESERVATION_NOT_OWNED)
        }
        if (reservation.isExpired(now)) {
            throw CoreException(ErrorType.INVALID_RESERVATION_STATE, "홀드가 만료된 예약은 결제할 수 없습니다.")
        }
        if (reservation.status != ReservationStatus.PENDING) {
            throw CoreException(ErrorType.INVALID_RESERVATION_STATE, "결제 대기(PENDING) 상태의 예약만 결제할 수 있습니다.")
        }
        paymentRepository.findByReservationId(reservationId)
            .firstOrNull { !it.status.allowsRetry }
            ?.let { return it }
        return paymentRepository.save(Payment.create(reservation, cardType, cardNo))
    }

    /**
     * PG 접수 성공 반영(CREATED → REQUESTED).
     * 콜백이 접수 응답보다 먼저 결과를 반영해 이미 종결됐다면 아무것도 바꾸지 않는다.
     */
    @Transactional
    fun markRequested(paymentId: Long, transactionKey: String): Payment {
        val payment = findWithLockOrThrow(paymentId)
        if (payment.status.isTerminal) {
            return payment
        }
        payment.markRequested(transactionKey)
        return payment
    }

    /** PG 접수 확정 실패 반영(CREATED → REQUEST_FAILED). 종결 상태면 no-op. */
    @Transactional
    fun markRequestFailed(paymentId: Long, reason: String): Payment {
        val payment = findWithLockOrThrow(paymentId)
        if (payment.status.isTerminal) {
            return payment
        }
        payment.markRequestFailed(reason)
        return payment
    }

    /**
     * PG 거래 상태 스냅샷을 결제에 반영한다(콜백·폴링 공용 진입점).
     * 행 잠금 + 종결 상태 no-op 으로 중복 콜백·동시 폴링에 멱등하다.
     *
     * @return 이번 호출로 일어난 변화 — 호출자(Facade)가 예약 확정/취소 후속 조치를 트리거하는 기준.
     */
    @Transactional
    fun applyPgResult(paymentId: Long, pgTransaction: PgTransaction): PgResultOutcome {
        val payment = findWithLockOrThrow(paymentId)
        if (payment.status.isTerminal) {
            return PgResultOutcome.NONE
        }
        return when (pgTransaction.status) {
            PgTransactionStatus.PENDING -> {
                if (payment.status == PaymentStatus.CREATED) {
                    payment.markRequested(pgTransaction.transactionKey)
                }
                PgResultOutcome.NONE
            }
            PgTransactionStatus.SUCCESS -> {
                payment.succeed(pgTransaction.transactionKey)
                PgResultOutcome.SUCCEEDED
            }
            PgTransactionStatus.FAILED -> {
                payment.fail(pgTransaction.transactionKey, pgTransaction.reason)
                PgResultOutcome.FAILED
            }
        }
    }

    private fun findWithLockOrThrow(paymentId: Long): Payment =
        paymentRepository.findWithLock(paymentId)
            ?: throw CoreException(ErrorType.PAYMENT_NOT_FOUND)
}

/** [PaymentService.applyPgResult] 가 이번 반영으로 일으킨 변화. */
enum class PgResultOutcome {
    /** 변화 없음 — 이미 종결됐거나 PG 가 아직 처리 중(PENDING). */
    NONE,

    /** 이번 반영으로 SUCCESS 도달 — 예약 확정 후속 조치 필요. */
    SUCCEEDED,

    /** 이번 반영으로 FAILED 도달 — 예약 취소(PAYMENT_FAILED) 후속 조치 필요. */
    FAILED,
}
