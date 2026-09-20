package com.eventhub.dto;

public record PaymentResponse(
    boolean success,
    String transactionId,
    String message,
    String bookingReference
) {}
