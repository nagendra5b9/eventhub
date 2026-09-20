package com.eventhub.service;

import com.eventhub.dto.BookingResponseDto;
import com.eventhub.dto.CheckoutRequest;
import com.eventhub.entity.*;
import com.eventhub.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final SeatLockService seatLockService;

    public BookingService(BookingRepository bookingRepository, SeatRepository seatRepository,
                          EventRepository eventRepository, UserRepository userRepository,
                          SeatLockService seatLockService) {
        this.bookingRepository = bookingRepository;
        this.seatRepository = seatRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.seatLockService = seatLockService;
    }

    @Transactional
    public Booking createPendingBooking(CheckoutRequest request, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        Event event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> new IllegalArgumentException("Event not found with ID: " + request.eventId()));

        // Pessimistic DB write lock on selected seats to prevent race conditions
        List<Seat> seats = seatRepository.findAllByIdWithLock(request.seatIds());
        if (seats.size() != request.seatIds().size()) {
            throw new IllegalArgumentException("One or more selected seats do not exist");
        }

        for (Seat seat : seats) {
            if (seat.getStatus() == SeatStatus.BOOKED) {
                throw new IllegalStateException("Seat " + seat.getSeatNumber() + " is already booked!");
            }
            // Check Redis lock ownership
            if (seatLockService.isSeatLocked(event.getId(), seat.getId())) {
                String owner = seatLockService.getLockOwner(event.getId(), seat.getId());
                if (!userEmail.equals(owner)) {
                    throw new IllegalStateException("Seat " + seat.getSeatNumber() + " is currently reserved by another customer!");
                }
            }
        }

        BigDecimal totalAmount = seats.stream()
                .map(Seat::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String refCode = "EH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Booking booking = new Booking(refCode, user, event, totalAmount);

        List<BookingItem> items = new ArrayList<>();
        for (Seat seat : seats) {
            items.add(new BookingItem(booking, seat, seat.getPrice()));
        }
        booking.setItems(items);

        return bookingRepository.save(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingResponseDto> getBookingsForUser(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        return bookingRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BookingResponseDto getBookingByReference(String reference) {
        Booking booking = bookingRepository.findByBookingReference(reference)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + reference));
        return mapToDto(booking);
    }

    public BookingResponseDto mapToDto(Booking booking) {
        List<String> seatNumbers = booking.getItems().stream()
                .map(item -> item.getSeat().getSeatNumber())
                .collect(Collectors.toList());

        return new BookingResponseDto(
                booking.getId(),
                booking.getBookingReference(),
                booking.getEvent().getTitle(),
                booking.getEvent().getVenue().getName(),
                seatNumbers,
                booking.getTotalAmount(),
                booking.getStatus(),
                booking.getCreatedAt()
        );
    }
}
