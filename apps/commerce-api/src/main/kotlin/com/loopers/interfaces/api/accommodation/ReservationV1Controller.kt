package com.loopers.interfaces.api.accommodation

import com.loopers.application.accommodation.ReservationFacade
import com.loopers.interfaces.api.ApiResponse
import jakarta.validation.Valid
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
        )
            .let { ReservationV1Dto.ReservationResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
