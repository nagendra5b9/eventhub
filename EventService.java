package com.eventhub.service;

import com.eventhub.dto.EventDto;
import com.eventhub.dto.SeatDto;
import com.eventhub.entity.Event;
import com.eventhub.entity.Seat;
import com.eventhub.entity.SeatStatus;
import com.eventhub.repository.EventRepository;
import com.eventhub.repository.SeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;
    private final SeatLockService seatLockService;

    public EventService(EventRepository eventRepository, SeatRepository seatRepository,
                        SeatLockService seatLockService) {
        this.eventRepository = eventRepository;
        this.seatRepository = seatRepository;
        this.seatLockService = seatLockService;
    }

    @Transactional(readOnly = true)
    public List<EventDto> getAllEvents() {
        return eventRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public EventDto getEventById(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found with ID: " + eventId));
        return mapToDto(event);
    }

    @Transactional(readOnly = true)
    public List<SeatDto> getSeatsForEvent(Long eventId, String currentUserEmail) {
        List<Seat> seats = seatRepository.findByEventIdOrderBySeatNumberAsc(eventId);

        return seats.stream().map(seat -> {
            SeatStatus effectiveStatus = seat.getStatus();
            boolean isLockedByMe = false;

            if (seat.getStatus() == SeatStatus.AVAILABLE) {
                if (seatLockService.isSeatLocked(eventId, seat.getId())) {
                    effectiveStatus = SeatStatus.LOCKED;
                    String owner = seatLockService.getLockOwner(eventId, seat.getId());
                    if (currentUserEmail != null && currentUserEmail.equals(owner)) {
                        isLockedByMe = true;
                    }
                }
            }

            return new SeatDto(
                    seat.getId(),
                    seat.getSeatNumber(),
                    seat.getRowLabel(),
                    effectiveStatus,
                    seat.getPrice(),
                    isLockedByMe
            );
        }).collect(Collectors.toList());
    }

    private EventDto mapToDto(Event event) {
        return new EventDto(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getCategory(),
                event.getEventDate(),
                event.getVenue().getName(),
                event.getVenue().getCity(),
                event.getPricePerSeat(),
                event.getTotalSeats(),
                event.getAvailableSeats()
        );
    }
}
