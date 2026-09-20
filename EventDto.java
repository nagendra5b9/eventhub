package com.eventhub.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EventDto(
    Long id,
    String title,
    String description,
    String category,
    LocalDateTime eventDate,
    String venueName,
    String venueCity,
    BigDecimal pricePerSeat,
    Integer totalSeats,
    Integer availableSeats
) {}
