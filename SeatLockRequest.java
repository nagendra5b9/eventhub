package com.eventhub.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SeatLockRequest(
    @NotNull Long eventId,
    @NotEmpty List<Long> seatIds
) {}
