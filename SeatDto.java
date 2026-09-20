package com.eventhub.dto;

import com.eventhub.entity.SeatStatus;
import java.math.BigDecimal;

public record SeatDto(
    Long id,
    String seatNumber,
    String rowLabel,
    SeatStatus status,
    BigDecimal price,
    boolean isLockedByMe
) {}
