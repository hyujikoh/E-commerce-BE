package com.loopers.infrastructure.payment.pg

import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PgPaymentRequest
import com.loopers.domain.payment.PgRequestResult
import com.loopers.domain.payment.PgTransactionStatus
import feign.FeignException
import feign.RetryableException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PgSimulatorGatewayTest {
    private val circuitCaller = mockk<PgSimulatorCircuitCaller>()
    private val gateway = PgSimulatorGateway(
        circuitCaller = circuitCaller,
        properties = PgSimulatorProperties(
            baseUrl = "http://localhost:8082",
            callbackUrl = "http://localhost:8080/api/v1/payments/callback",
        ),
    )

    private val request = PgPaymentRequest(
        guestId = 1L,
        orderId = "000042",
        cardType = CardType.SAMSUNG,
        cardNo = "1234-5678-9012-3456",
        amount = 100_000L,
    )

    private fun successMeta() = PgSimulatorDto.Response.Meta(result = "SUCCESS", errorCode = null, message = null)

    @DisplayName("결제 생성을 요청할 때,")
    @Nested
    inner class RequestPayment {
        @DisplayName("PG 가 접수하면 transactionKey 를 담은 Accepted 를 반환한다.")
        @Test
        fun returnsAccepted_whenPgAcceptsRequest() {
            every { circuitCaller.requestPayment(any(), any()) } returns PgSimulatorDto.Response(
                meta = successMeta(),
                data = PgSimulatorDto.TransactionResponse(transactionKey = "tx-123", status = "PENDING", reason = null),
            )

            val result = gateway.requestPayment(request)

            assertThat(result).isEqualTo(PgRequestResult.Accepted("tx-123"))
        }

        @DisplayName("PG 가 에러 응답을 주면 Rejected 를 반환한다 — 이 PG 는 거래 생성 전 단계에서만 에러를 반환하므로 거래 없음이 확실하다.")
        @Test
        fun returnsRejected_whenPgRespondsWithError() {
            every { circuitCaller.requestPayment(any(), any()) } throws mockk<FeignException>(relaxed = true)

            val result = gateway.requestPayment(request)

            assertThat(result).isInstanceOf(PgRequestResult.Rejected::class.java)
        }

        @DisplayName("서킷이 오픈이면 호출 없이 Rejected 를 반환한다 — 호출 자체를 안 했으므로 거래 없음이 확실하다.")
        @Test
        fun returnsRejected_whenCircuitIsOpen() {
            val circuitBreaker = CircuitBreaker.ofDefaults(PgSimulatorCircuitCaller.CIRCUIT_PAYMENT)
            every { circuitCaller.requestPayment(any(), any()) } throws
                CallNotPermittedException.createCallNotPermittedException(circuitBreaker)

            val result = gateway.requestPayment(request)

            assertThat(result).isInstanceOf(PgRequestResult.Rejected::class.java)
        }

        @DisplayName("타임아웃 등 응답 유실이면 Unknown 을 반환한다 — 거래 생성 여부를 모르므로 실패 확정하지 않는다.")
        @Test
        fun returnsUnknown_whenResponseIsLost() {
            every { circuitCaller.requestPayment(any(), any()) } throws mockk<RetryableException>(relaxed = true)

            val result = gateway.requestPayment(request)

            assertThat(result).isInstanceOf(PgRequestResult.Unknown::class.java)
        }

        @DisplayName("정상 응답이라도 거래 정보가 비어 있으면 Rejected 를 반환한다.")
        @Test
        fun returnsRejected_whenResponseHasNoData() {
            every { circuitCaller.requestPayment(any(), any()) } returns PgSimulatorDto.Response(
                meta = successMeta(),
                data = null,
            )

            val result = gateway.requestPayment(request)

            assertThat(result).isInstanceOf(PgRequestResult.Rejected::class.java)
        }
    }

    @DisplayName("거래를 조회할 때,")
    @Nested
    inner class FindTransaction {
        @DisplayName("단건 조회 성공 시 PG 거래 스냅샷으로 매핑한다.")
        @Test
        fun mapsToPgTransaction_whenFound() {
            every { circuitCaller.getTransaction("1", "tx-123") } returns PgSimulatorDto.Response(
                meta = successMeta(),
                data = PgSimulatorDto.TransactionDetailResponse(
                    transactionKey = "tx-123",
                    orderId = "000042",
                    cardType = "SAMSUNG",
                    cardNo = "1234-5678-9012-3456",
                    amount = 100_000L,
                    status = "SUCCESS",
                    reason = null,
                ),
            )

            val transaction = gateway.findTransaction(guestId = 1L, transactionKey = "tx-123")

            assertThat(transaction?.transactionKey).isEqualTo("tx-123")
            assertThat(transaction?.status).isEqualTo(PgTransactionStatus.SUCCESS)
        }

        @DisplayName("단건 조회가 실패하면 예외 대신 null 을 반환한다.")
        @Test
        fun returnsNull_whenQueryFails() {
            every { circuitCaller.getTransaction(any(), any()) } throws mockk<FeignException>(relaxed = true)

            assertThat(gateway.findTransaction(guestId = 1L, transactionKey = "tx-123")).isNull()
        }

        @DisplayName("주문 ID 목록 조회 성공 시 거래 목록으로 매핑한다.")
        @Test
        fun mapsToPgTransactions_whenOrderFound() {
            every { circuitCaller.getTransactionsByOrderId("1", "000042") } returns PgSimulatorDto.Response(
                meta = successMeta(),
                data = PgSimulatorDto.OrderResponse(
                    orderId = "000042",
                    transactions = listOf(
                        PgSimulatorDto.TransactionResponse(transactionKey = "tx-123", status = "FAILED", reason = "한도 초과"),
                    ),
                ),
            )

            val transactions = gateway.findTransactionsByOrderId(guestId = 1L, orderId = "000042")

            assertThat(transactions).hasSize(1)
            assertThat(transactions[0].status).isEqualTo(PgTransactionStatus.FAILED)
            assertThat(transactions[0].orderId).isEqualTo("000042")
        }

        @DisplayName("주문 ID 목록 조회가 실패하면 예외 대신 빈 목록을 반환한다.")
        @Test
        fun returnsEmptyList_whenOrderQueryFails() {
            every { circuitCaller.getTransactionsByOrderId(any(), any()) } throws mockk<FeignException>(relaxed = true)

            assertThat(gateway.findTransactionsByOrderId(guestId = 1L, orderId = "000042")).isEmpty()
        }
    }
}
