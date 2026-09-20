package com.eventhub.service;

import com.eventhub.dto.BookingKafkaMessage;
import com.eventhub.dto.PaymentRequest;
import com.eventhub.dto.PaymentResponse;
import com.eventhub.entity.*;
import com.eventhub.repository.BookingRepository;
import com.eventhub.repository.EventRepository;
import com.eventhub.repository.PaymentRepository;
import com.eventhub.repository.SeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;
    private final EventRepository eventRepository;
    private final SeatLockService seatLockService;
    private final BookingKafkaProducer kafkaProducer;

    public PaymentService(PaymentRepository paymentRepository, BookingRepository bookingRepository,
                          SeatRepository seatRepository, EventRepository eventRepository,
                          SeatLockService seatLockService, BookingKafkaProducer kafkaProducer) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.seatRepository = seatRepository;
        this.eventRepository = eventRepository;
        this.seatLockService = seatLockService;
        this.kafkaProducer = kafkaProducer;
    }

    @Transactional
    public PaymentResponse processPayment(PaymentRequest request, String userEmail) {
        // 1. Idempotency check: if this transaction was already processed, return previous result
        Optional<Payment> existingPayment = paymentRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existingPayment.isPresent()) {
            Payment payment = existingPayment.get();
            return new PaymentResponse(
                    payment.getStatus() == PaymentStatus.SUCCESS,
                    payment.getTransactionId(),
                    "Payment already processed (Idempotent response)",
                    payment.getBooking().getBookingReference()
            );
        }

        Booking booking = bookingRepository.findById(request.bookingId())
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + request.bookingId()));

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            return new PaymentResponse(true, "ALREADY_CONFIRMED", "Booking is already confirmed", booking.getBookingReference());
        }

        String transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();

        // 2. Simulate failure if requested
        if (request.simulateFailure()) {
            Payment payment = new Payment(booking, transactionId, request.idempotencyKey(), PaymentStatus.FAILED, request.amount());
            paymentRepository.save(payment);
            booking.setStatus(BookingStatus.CANCELLED);
            bookingRepository.save(booking);

            // Release temporary Redis locks on payment failure
            List<Long> seatIds = booking.getItems().stream().map(i -> i.getSeat().getId()).collect(Collectors.toList());
            seatLockService.releaseSeats(booking.getEvent().getId(), seatIds, userEmail);

            return new PaymentResponse(false, transactionId, "Simulated payment transaction failed", booking.getBookingReference());
        }

        // 3. Payment Success: mark seats as BOOKED permanently
        List<Seat> seats = booking.getItems().stream().map(BookingItem::getSeat).collect(Collectors.toList());
        for (Seat seat : seats) {
            seat.setStatus(SeatStatus.BOOKED);
            seatRepository.save(seat);
        }

        // Decrement available seats on Event
        Event event = booking.getEvent();
        event.setAvailableSeats(Math.max(0, event.getAvailableSeats() - seats.size()));
        eventRepository.save(event);

        // Update booking status
        booking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);

        // Persist payment record
        Payment payment = new Payment(booking, transactionId, request.idempotencyKey(), PaymentStatus.SUCCESS, request.amount());
        paymentRepository.save(payment);

        // Release Redis temporary locks since seats are now persistently BOOKED in MySQL
        List<Long> seatIds = seats.stream().map(Seat::getId).collect(Collectors.toList());
        seatLockService.forceReleaseSeats(event.getId(), seatIds);

        // 4. Publish Kafka event asynchronously
        List<String> seatNumbers = seats.stream().map(Seat::getSeatNumber).collect(Collectors.toList());
        BookingKafkaMessage kafkaMsg = new BookingKafkaMessage(
                booking.getId(),
                booking.getBookingReference(),
                userEmail,
                booking.getUser().getName(),
                event.getTitle(),
                seatNumbers,
                booking.getTotalAmount(),
                LocalDateTime.now()
        );
        kafkaProducer.sendBookingConfirmedEvent(kafkaMsg);

        return new PaymentResponse(true, transactionId, "Payment successful! Booking confirmed.", booking.getBookingReference());
    }
}
