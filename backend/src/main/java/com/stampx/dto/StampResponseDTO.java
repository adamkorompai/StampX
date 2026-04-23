package com.stampx.dto;

import java.util.UUID;

public record StampResponseDTO(
        UUID customerId,
        String passSerial,
        int stampCount,
        int stampGoal,
        boolean rewardEarned,
        UUID rewardId   // null when no reward was earned this stamp
) {}
