package com.eventhub.service;

import com.eventhub.entity.*;
import com.eventhub.repository.EventRepository;
import com.eventhub.repository.SeatRepository;
import com.eventhub.repository.UserRepository;
import com.eventhub.repository.VenueRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final VenueRepository venueRepository;
    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, VenueRepository venueRepository,
                           EventRepository eventRepository, SeatRepository seatRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.venueRepository = venueRepository;
        this.eventRepository = eventRepository;
        this.seatRepository = seatRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }

        // 1. Seed Users
        User admin = new User("Admin Manager", "admin@eventhub.com", passwordEncoder.encode("admin123"), Role.ADMIN);
        User customer = new User("Nagendra Reddy", "user@eventhub.com", passwordEncoder.encode("user123"), Role.CUSTOMER);
        userRepository.save(admin);
        userRepository.save(customer);

        // 2. Seed Venues
        Venue venue1 = new Venue("Hitex Exhibition Center", "Hyderabad", "Hitec City, Madhapur, Hyderabad", 500);
        Venue venue2 = new Venue("Cyber Arena", "Bengaluru", "Electronic City Phase 1, Bengaluru", 400);
        venueRepository.save(venue1);
        venueRepository.save(venue2);

        // 3. Seed Events
        Event event1 = new Event(
                "Global Tech & AI Summit 2026",
                "Join industry leaders in Java, Distributed Systems, Cloud and AI for 2 days of masterclasses.",
                "Technology",
                LocalDateTime.now().plusDays(14),
                venue1,
                new BigDecimal("1500.00"),
                48,
                48
        );
        eventRepository.save(event1);

        Event event2 = new Event(
                "Rock Night Live in Concert",
                "An electrifying evening of rock, indie melodies, and live performances by top bands.",
                "Concert",
                LocalDateTime.now().plusDays(21),
                venue2,
                new BigDecimal("2200.00"),
                48,
                48
        );
        eventRepository.save(event2);

        // 4. Seed Seating Grids (Rows A-F, Seats 1-8 => 48 seats each)
        seedSeatsForEvent(event1);
        seedSeatsForEvent(event2);
    }

    private void seedSeatsForEvent(Event event) {
        String[] rows = {"A", "B", "C", "D", "E", "F"};
        for (String row : rows) {
            for (int col = 1; col <= 8; col++) {
                String seatNum = row + col;
                BigDecimal price = "A".equals(row) || "B".equals(row)
                        ? event.getPricePerSeat().multiply(new BigDecimal("1.25")) // Premium row
                        : event.getPricePerSeat();
                Seat seat = new Seat(event, seatNum, row, price);
                seatRepository.save(seat);
            }
        }
    }
}
