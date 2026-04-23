package com.stampx.controller;

import com.stampx.dto.DashboardStatsDTO;
import com.stampx.dto.RewardDTO;
import com.stampx.model.Shop;
import com.stampx.service.DashboardService;
import com.stampx.service.QRCodeService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("isAuthenticated()")
public class DashboardController {

    private final DashboardService dashboardService;
    private final QRCodeService qrCodeService;

    public DashboardController(DashboardService dashboardService, QRCodeService qrCodeService) {
        this.dashboardService = dashboardService;
        this.qrCodeService = qrCodeService;
    }

    @GetMapping("/stats")
    public DashboardStatsDTO stats() {
        return dashboardService.getStats(shopId());
    }

    @GetMapping("/rewards/pending")
    public List<RewardDTO> pendingRewards() {
        return dashboardService.getPendingRewards(shopId());
    }

    @PostMapping("/rewards/{rewardId}/redeem")
    public ResponseEntity<Map<String, String>> redeem(@PathVariable UUID rewardId) {
        dashboardService.redeemReward(shopId(), rewardId);
        return ResponseEntity.ok(Map.of("message", "Redeemed"));
    }

    @GetMapping(value = "/qrcode", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qrCode() {
        Shop shop = currentShop();
        byte[] png = qrCodeService.generateQRCodeForShop(shop.getSlug());
        return ResponseEntity.ok()
                .header("Content-Disposition", "inline; filename=\"qrcode.png\"")
                .body(png);
    }

    private UUID shopId() {
        return currentShop().getId();
    }

    private Shop currentShop() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (Shop) auth.getPrincipal();
    }
}
