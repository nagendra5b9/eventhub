package com.eventhub.service;

import com.eventhub.config.KafkaConfig;
import com.eventhub.dto.BookingKafkaMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class BookingKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(BookingKafkaProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public BookingKafkaProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void sendBookingConfirmedEvent(BookingKafkaMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            kafkaTemplate.send(KafkaConfig.BOOKING_EVENTS_TOPIC, message.bookingReference(), payload);
            log.info("Published booking event to Kafka topic [{}]: {}", KafkaConfig.BOOKING_EVENTS_TOPIC, message.bookingReference());
        } catch (Exception e) {
            log.error("Failed to serialize or publish Kafka booking event: {}", e.getMessage());
        }
    }
}
