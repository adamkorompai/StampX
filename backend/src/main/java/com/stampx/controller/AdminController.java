package com.stampx.controller;

import com.stampx.dto.ShopRegistrationDTO;
import com.stampx.model.Shop;
import com.stampx.service.ShopService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ShopService shopService;
    private final byte[] adminSecretBytes;

    public AdminController(ShopService shopService,
                           @Value("${app.admin-secret}") String adminSecret) {
        this.shopService = shopService;
        this.adminSecretBytes = adminSecret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Manually onboards a shop. Protected by X-Admin-Secret header.
     * Uses constant-time comparison to prevent timing attacks.
     */
    @PostMapping("/onboard")
    public ResponseEntity<Map<String, Object>> onboard(
            @RequestHeader(value = "X-Admin-Secret", required = false) String providedSecret,
            @RequestBody @Valid ShopRegistrationDTO dto) {

        if (providedSecret == null || !MessageDigest.isEqual(
                adminSecretBytes,
                providedSecret.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "FORBIDDEN", "message", "Invalid admin secret"));
        }

        ShopService.RegistrationResult result = shopService.registerShop(dto);
        Shop shop = result.shop();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", shop.getId(),
                "name", shop.getName(),
                "slug", shop.getSlug(),
                "email", shop.getEmail(),
                "stampGoal", shop.getStampGoal(),
                "apiKey", result.rawApiKey()
        ));
    }
}
