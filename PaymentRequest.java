package com.eventhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PaymentRequest(
    @NotNull Long bookingId,
    @NotBlank String idempotencyKey,
    @NotNull BigDecimal amount,
    boolean simulateFailure
) {}
