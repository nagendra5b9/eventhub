package com.eventhub.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BookingKafkaMessage(
    Long bookingId,
    String bookingReference,
    String userEmail,
    String userName,
    String eventTitle,
    List<String> seatNumbers,
    BigDecimal totalAmount,
    LocalDateTime timestamp
) {}
