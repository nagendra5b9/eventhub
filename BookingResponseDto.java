package com.eventhub.dto;

import com.eventhub.entity.BookingStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BookingResponseDto(
    Long bookingId,
    String bookingReference,
    String eventTitle,
    String venueName,
    List<String> seatNumbers,
    BigDecimal totalAmount,
    BookingStatus status,
    LocalDateTime createdAt
) {}
