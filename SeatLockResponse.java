package com.eventhub.dto;

import java.util.List;

public record SeatLockResponse(
    boolean success,
    String message,
    List<Long> lockedSeatIds,
    long remainingSeconds
) {}
