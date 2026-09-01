package com.loopers.infrastructure.payment

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class PaymentRepositoryImpl(
    private val paymentJpaRepository: PaymentJpaRepository,
) : PaymentRepository {
    override fun save(payment: Payment): Payment = paymentJpaRepository.save(payment)

    override fun find(id: Long): Payment? = paymentJpaRepository.findByIdOrNull(id)

    override fun findWithLock(id: Long): Payment? = paymentJpaRepository.findWithLockById(id)

    override fun findByReservationId(reservationId: Long): List<Payment> =
        paymentJpaRepository.findByReservationId(reservationId)

    override fun findResultPending(threshold: ZonedDateTime, limit: Int): List<Payment> =
        paymentJpaRepository.findByStatusInAndUpdatedAtBefore(
            statuses = listOf(PaymentStatus.CREATED, PaymentStatus.REQUESTED),
            threshold = threshold,
            pageable = PageRequest.of(0, limit),
        )
}
