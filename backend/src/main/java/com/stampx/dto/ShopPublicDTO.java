package com.stampx.dto;

public record ShopPublicDTO(
        String slug,
        String name,
        String logoUrl,
        String primaryColor,
        int stampGoal,
        String rewardDescription
) {}
