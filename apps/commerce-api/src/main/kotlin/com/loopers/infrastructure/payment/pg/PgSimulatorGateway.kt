package com.loopers.infrastructure.payment.pg

import com.loopers.domain.payment.PgPaymentGateway
import com.loopers.domain.payment.PgPaymentRequest
import com.loopers.domain.payment.PgRequestResult
import com.loopers.domain.payment.PgTransaction
import feign.FeignException
import feign.RetryableException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * [PgPaymentGateway] 구현. 예외를 밖으로 전파하지 않고 결과 타입으로 흡수한다(Fallback) —
 * PG 장애가 내부 장애로 번지지 않게 하기 위함이다.
 *
 * 결제 생성 실패의 3분법(확실성 기준):
 * - 에러 응답([FeignException]): PG 시뮬레이터는 거래 생성 전 단계에서만 에러를 반환한다(스펙 확인)
 *   → 거래 없음 확실 → [PgRequestResult.Rejected] (사용자 즉시 재시도 가능)
 * - 서킷 오픈([CallNotPermittedException]): 호출 자체를 안 했다 → 거래 없음 확실 → Rejected
 * - 타임아웃 등 응답 유실([RetryableException]): 거래 생성 여부 불명 → [PgRequestResult.Unknown]
 *   (함부로 실패 확정하지 않고 상태 동기화가 orderId 조회로 사후 확정한다)
 */
@Component
class PgSimulatorGateway(
    private val circuitCaller: PgSimulatorCircuitCaller,
    private val properties: PgSimulatorProperties,
) : PgPaymentGateway {
    override fun requestPayment(request: PgPaymentRequest): PgRequestResult = try {
        val data = circuitCaller.requestPayment(
            userId = request.guestId.toString(),
            request = PgSimulatorDto.PaymentRequest(
                orderId = request.orderId,
                cardType = request.cardType.name,
                cardNo = request.cardNo,
                amount = request.amount,
                callbackUrl = properties.callbackUrl,
            ),
        ).data
        if (data == null) {
            PgRequestResult.Rejected("PG 응답에 거래 정보가 없습니다.")
        } else {
            PgRequestResult.Accepted(data.transactionKey)
        }
    } catch (e: CallNotPermittedException) {
        logger.warn("PG 서킷 오픈 — 결제 요청 차단. orderId={}", request.orderId)
        PgRequestResult.Rejected("PG 연동이 불안정하여 요청을 차단했습니다. 잠시 후 다시 시도해주세요.")
    } catch (e: RetryableException) {
        logger.warn("PG 응답 유실(타임아웃) — 거래 생성 여부 불명. orderId={}, cause={}", request.orderId, e.message)
        PgRequestResult.Unknown("PG 응답을 받지 못했습니다. 결제 상태를 확인 중입니다.")
    } catch (e: FeignException) {
        logger.warn("PG 요청 거절. orderId={}, status={}, body={}", request.orderId, e.status(), e.contentUTF8())
        PgRequestResult.Rejected("PG가 결제 요청을 거절했습니다. 잠시 후 다시 시도해주세요.")
    }

    override fun findTransaction(guestId: Long, transactionKey: String): PgTransaction? = try {
        circuitCaller.getTransaction(guestId.toString(), transactionKey).data?.toPgTransaction()
    } catch (e: Exception) {
        logger.warn("PG 거래 단건 조회 실패. transactionKey={}, cause={}", transactionKey, e.message)
        null
    }

    override fun findTransactionsByOrderId(guestId: Long, orderId: String): List<PgTransaction>? = try {
        circuitCaller.getTransactionsByOrderId(guestId.toString(), orderId).data?.toPgTransactions().orEmpty()
    } catch (e: FeignException.NotFound) {
        // PG 는 주문에 거래가 하나도 없으면 404 를 준다 — "거래 없음 확정"이므로 빈 목록으로 구분해 반환한다.
        emptyList()
    } catch (e: Exception) {
        logger.warn("PG 주문 거래 목록 조회 실패 — 거래 존재 여부 불명. orderId={}, cause={}", orderId, e.message)
        null
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PgSimulatorGateway::class.java)
    }
}
