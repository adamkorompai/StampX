package com.stampx.dto;

import java.time.Instant;
import java.util.UUID;

public record RewardDTO(
        UUID id,
        UUID customerId,
        String passSerial,
        int stampCount,
        Instant createdAt,
        Instant redeemedAt
) {}
