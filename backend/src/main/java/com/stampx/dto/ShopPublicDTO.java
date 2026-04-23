package com.stampx.dto;

public record ShopPublicDTO(
        String name,
        String logoUrl,
        String primaryColor,
        int stampGoal,
        String rewardDescription
) {}
