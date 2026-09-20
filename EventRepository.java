package com.eventhub.repository;

import com.eventhub.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findByCategoryIgnoreCase(String category);
    List<Event> findByVenueCityIgnoreCase(String city);
}
