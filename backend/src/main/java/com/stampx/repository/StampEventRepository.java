package com.stampx.repository;

import com.stampx.model.StampEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface StampEventRepository extends JpaRepository<StampEvent, UUID> {
    long countByShopIdAndStampedAtBetween(UUID shopId, Instant start, Instant end);
}
