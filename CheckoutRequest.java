package com.eventhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CheckoutRequest(
    @NotNull Long eventId,
    @NotEmpty List<Long> seatIds,
    @NotBlank String idempotencyKey
) {}
