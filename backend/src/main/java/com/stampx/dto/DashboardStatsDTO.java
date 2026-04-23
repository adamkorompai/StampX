package com.stampx.dto;

public record DashboardStatsDTO(
        long totalCustomers,
        long stampsToday,
        long pendingRewardsCount
) {}
