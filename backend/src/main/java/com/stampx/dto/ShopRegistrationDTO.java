package com.stampx.dto;

import jakarta.validation.constraints.*;

public record ShopRegistrationDTO(
        @NotBlank @Size(min = 2, max = 100) String name,
        @NotBlank @Pattern(regexp = "^[a-z0-9-]+$", message = "slug must be lowercase letters, numbers, and hyphens only")
        @Size(min = 2, max = 50) String slug,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, message = "password must be at least 8 characters") String password,
        @Min(1) @Max(100) int stampGoal,
        @NotBlank String rewardDescription,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "primaryColor must be a valid hex color e.g. #3B82F6") String primaryColor,
        String logoUrl
) {}
