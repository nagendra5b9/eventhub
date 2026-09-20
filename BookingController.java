package com.eventhub.controller;

import com.eventhub.dto.*;
import com.eventhub.entity.Booking;
import com.eventhub.service.BookingService;
import com.eventhub.service.SeatLockService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final SeatLockService seatLockService;

    public BookingController(BookingService bookingService, SeatLockService seatLockService) {
        this.bookingService = bookingService;
        this.seatLockService = seatLockService;
    }

    @PostMapping("/lock-seats")
    public ResponseEntity<SeatLockResponse> lockSeats(@Valid @RequestBody SeatLockRequest request,
                                                      Authentication authentication) {
        String userEmail = authentication.getName();
        boolean locked = seatLockService.lockSeats(request.eventId(), request.seatIds(), userEmail);

        if (!locked) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new SeatLockResponse(false, "One or more seats are currently locked by someone else. Please select other seats.", request.seatIds(), 0));
        }

        long ttl = seatLockService.getRemainingLockTimeSeconds(request.eventId(), request.seatIds().get(0));
        return ResponseEntity.ok(new SeatLockResponse(true, "Seats temporarily reserved for 10 minutes.", request.seatIds(), ttl));
    }

    @PostMapping("/checkout")
    public ResponseEntity<BookingResponseDto> checkout(@Valid @RequestBody CheckoutRequest request,
                                                       Authentication authentication) {
        String userEmail = authentication.getName();
        Booking booking = bookingService.createPendingBooking(request, userEmail);
        return ResponseEntity.ok(bookingService.mapToDto(booking));
    }

    @GetMapping("/my-bookings")
    public ResponseEntity<List<BookingResponseDto>> getMyBookings(Authentication authentication) {
        String userEmail = authentication.getName();
        return ResponseEntity.ok(bookingService.getBookingsForUser(userEmail));
    }

    @GetMapping("/{reference}")
    public ResponseEntity<BookingResponseDto> getBookingByReference(@PathVariable String reference) {
        return ResponseEntity.ok(bookingService.getBookingByReference(reference));
    }
}
