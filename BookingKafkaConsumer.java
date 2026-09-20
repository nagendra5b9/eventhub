package com.eventhub.service;

import com.eventhub.config.KafkaConfig;
import com.eventhub.dto.BookingKafkaMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class BookingKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookingKafkaConsumer.class);
    private final ObjectMapper objectMapper;

    public BookingKafkaConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaConfig.BOOKING_EVENTS_TOPIC, groupId = "eventhub-booking-group")
    public void consumeBookingEvent(String messageJson) {
        try {
            BookingKafkaMessage bookingMessage = objectMapper.readValue(messageJson, BookingKafkaMessage.class);
            log.info("==================================================================");
            log.info("[KAFKA WORKER] Received Booking Confirmation for Reference: {}", bookingMessage.bookingReference());
            log.info("[KAFKA WORKER] Customer: {} <{}>", bookingMessage.userName(), bookingMessage.userEmail());
            log.info("[KAFKA WORKER] Event: {}", bookingMessage.eventTitle());
            log.info("[KAFKA WORKER] Seats: {}", bookingMessage.seatNumbers());
            log.info("[KAFKA WORKER] Total Amount Paid: INR {}", bookingMessage.totalAmount());
            log.info("[KAFKA WORKER] Simulating PDF Ticket generation and confirmation email dispatch...");
            log.info("==================================================================");
        } catch (Exception e) {
            log.error("Error processing Kafka message: {}", e.getMessage());
        }
    }
}
