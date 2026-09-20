package com.eventhub.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "seats", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"event_id", "seat_number"})
})
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "seat_number", nullable = false)
    private String seatNumber; // e.g. "A1", "A2", "B1"

    @Column(name = "row_label", nullable = false)
    private String rowLabel; // e.g. "A", "B"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status = SeatStatus.AVAILABLE;

    @Column(nullable = false)
    private BigDecimal price;

    @Version
    private Long version = 0L; // Optimistic concurrency control

    public Seat() {}

    public Seat(Event event, String seatNumber, String rowLabel, BigDecimal price) {
        this.event = event;
        this.seatNumber = seatNumber;
        this.rowLabel = rowLabel;
        this.price = price;
        this.status = SeatStatus.AVAILABLE;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Event getEvent() { return event; }
    public void setEvent(Event event) { this.event = event; }

    public String getSeatNumber() { return seatNumber; }
    public void setSeatNumber(String seatNumber) { this.seatNumber = seatNumber; }

    public String getRowLabel() { return rowLabel; }
    public void setRowLabel(String rowLabel) { this.rowLabel = rowLabel; }

    public SeatStatus getStatus() { return status; }
    public void setStatus(SeatStatus status) { this.status = status; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
