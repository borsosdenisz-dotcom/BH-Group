package com.bhstays.pms.controller;

import com.bhstays.pms.common.response.ApiResponse;
import com.bhstays.pms.dto.latecheckout.LateCheckoutRequestCreateRequest;
import com.bhstays.pms.dto.latecheckout.LateCheckoutRequestResponse;
import com.bhstays.pms.dto.messaging.MessageCreateRequest;
import com.bhstays.pms.dto.messaging.MessageResponse;
import com.bhstays.pms.dto.payment.CheckoutSessionResponse;
import com.bhstays.pms.dto.property.PriceQuoteResponse;
import com.bhstays.pms.dto.reservation.CancellationQuoteResponse;
import com.bhstays.pms.dto.publicapi.PublicBookingCheckoutResponse;
import com.bhstays.pms.dto.publicapi.PublicBookingRequest;
import com.bhstays.pms.dto.publicapi.PublicBookingUpdateRequest;
import com.bhstays.pms.dto.publicapi.PublicReservationResponse;
import com.bhstays.pms.dto.reservation.AvailabilityResponse;
import com.bhstays.pms.service.LateCheckoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bhstays.pms.service.PublicReservationService;
@RestController
@RequestMapping("/api/v1/public/reservations")
@RequiredArgsConstructor
@Tag(name = "Public Booking Engine", description = "Unauthenticated property search and booking")
public class PublicReservationController {

    private final PublicReservationService publicReservationService;
    private final LateCheckoutService lateCheckoutService;

    @GetMapping("/availability")
    @Operation(summary = "Check property availability for a date range")
    public ResponseEntity<ApiResponse<AvailabilityResponse>> availability(
            @RequestParam UUID propertyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut) {
        return ResponseEntity.ok(ApiResponse.success(
                publicReservationService.availability(propertyId, checkIn, checkOut)));
    }

    @GetMapping("/quote")
    @Operation(summary = "Get an itemized price quote for a date range")
    public ResponseEntity<ApiResponse<PriceQuoteResponse>> quote(
            @RequestParam UUID propertyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(defaultValue = "1") int guests) {
        return ResponseEntity.ok(ApiResponse.success(
                publicReservationService.quote(propertyId, checkIn, checkOut, guests)));
    }

    /**
     * Card-only: holds the dates and returns the Stripe Checkout URL to send
     * the guest to. 503 when online booking is unavailable (no Stripe, or
     * Stripe could not open the session - nothing is held then); 400 for
     * any payment method other than ONLINE_CARD.
     */
    @PostMapping
    @Operation(summary = "Hold a booking and open its card payment (hosted Stripe Checkout)")
    public ResponseEntity<ApiResponse<PublicBookingCheckoutResponse>> create(
            @Valid @RequestBody PublicBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                publicReservationService.createBooking(request),
                "Perioada este reținută. Finalizează plata cu cardul pentru confirmare."));
    }

    /**
     * Opens a hosted Stripe Checkout session for a booking still on hold. The
     * token is the only input - the amount is recomputed server-side, so
     * there is nothing here a caller could tamper with. Returns 503 when
     * online payment is unavailable.
     */
    @PostMapping("/{token}/checkout")
    @Operation(summary = "Start a card payment for a held booking (hosted Stripe Checkout)")
    public ResponseEntity<ApiResponse<CheckoutSessionResponse>> checkout(@PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.success(publicReservationService.createCheckoutSession(token)));
    }

    @GetMapping("/manage/{token}")
    @Operation(summary = "Get a reservation by its management token")
    public ResponseEntity<ApiResponse<PublicReservationResponse>> getByToken(@PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.success(publicReservationService.getByToken(token)));
    }

    @PutMapping("/manage/{token}")
    @Operation(summary = "Modify a reservation using its management token")
    public ResponseEntity<ApiResponse<PublicReservationResponse>> update(
            @PathVariable String token, @Valid @RequestBody PublicBookingUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                publicReservationService.updateByToken(token, request), "Rezervarea a fost actualizată"));
    }

    @PostMapping("/manage/{token}/cancel")
    @Operation(summary = "Cancel a reservation using its management token")
    public ResponseEntity<ApiResponse<PublicReservationResponse>> cancel(@PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.success(
                publicReservationService.cancelByToken(token), "Rezervarea a fost anulată"));
    }

    @GetMapping("/manage/{token}/cancellation-quote")
    @Operation(summary = "Preview the refund a cancellation would trigger right now")
    public ResponseEntity<ApiResponse<CancellationQuoteResponse>> cancellationQuote(@PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.success(publicReservationService.cancellationQuoteByToken(token)));
    }

    @GetMapping("/manage/{token}/messages")
    @Operation(summary = "List messages exchanged with staff about this reservation")
    public ResponseEntity<ApiResponse<List<MessageResponse>>> listMessages(@PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.success(publicReservationService.listMessagesByToken(token)));
    }

    @PostMapping("/manage/{token}/messages")
    @Operation(summary = "Send a message to staff about this reservation")
    public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(
            @PathVariable String token, @Valid @RequestBody MessageCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                publicReservationService.sendMessageByToken(token, request.body())));
    }

    @GetMapping("/manage/{token}/late-checkout")
    @Operation(summary = "Get the late checkout request for this reservation, if any")
    public ResponseEntity<ApiResponse<LateCheckoutRequestResponse>> getLateCheckout(@PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.success(
                lateCheckoutService.getByManagementToken(token).orElse(null)));
    }

    @PostMapping("/manage/{token}/late-checkout")
    @Operation(summary = "Request late checkout for this reservation")
    public ResponseEntity<ApiResponse<LateCheckoutRequestResponse>> requestLateCheckout(
            @PathVariable String token,
            @Valid @RequestBody(required = false) LateCheckoutRequestCreateRequest request) {
        String note = request != null ? request.guestNote() : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                lateCheckoutService.requestByManagementToken(token, note), "Cererea a fost trimisă"));
    }
}
