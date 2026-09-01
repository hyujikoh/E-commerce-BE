package com.loopers.interfaces.api.accommodation

import com.loopers.application.accommodation.ReservationFacade
import com.loopers.interfaces.api.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/reservations")
class ReservationV1Controller(
    private val reservationFacade: ReservationFacade,
) : ReservationV1ApiSpec {
    @PostMapping
    override fun create(
        @Valid @RequestBody request: ReservationV1Dto.CreateRequest,
    ): ApiResponse<ReservationV1Dto.ReservationResponse> {
        return reservationFacade.create(
            guestId = request.guestId,
            roomTypeId = request.roomTypeId,
            checkIn = request.checkIn,
            checkOut = request.checkOut,
            couponId = request.couponId,
        )
            .let { ReservationV1Dto.ReservationResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    // NOTE: 결제 성공 확정은 PG 콜백(PaymentFacade)이 트리거한다. 이 엔드포인트는 수동 운영·테스트용.
    @PostMapping("/{reservationId}/confirm")
    override fun confirm(
        @PathVariable reservationId: Long,
    ): ApiResponse<ReservationV1Dto.ReservationResponse> {
        return reservationFacade.confirm(reservationId)
            .let { ReservationV1Dto.ReservationResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @PostMapping("/{reservationId}/cancel")
    override fun cancel(
        @PathVariable reservationId: Long,
        @Valid @RequestBody request: ReservationV1Dto.CancelRequest,
    ): ApiResponse<ReservationV1Dto.ReservationResponse> {
        return reservationFacade.cancel(reservationId, request.guestId)
            .let { ReservationV1Dto.ReservationResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
