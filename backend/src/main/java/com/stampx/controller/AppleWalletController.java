package com.stampx.controller;

import com.stampx.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Implements the Apple Wallet Web Service protocol endpoints under /v1/.
 * Depends on WalletService interface (qualified as "appleWalletService") so
 * an AndroidWalletController can be added later with a different qualifier.
 *
 * Protocol reference: https://developer.apple.com/documentation/walletpasses/adding-a-web-service-to-update-passes
 */
@RestController
public class AppleWalletController {

    private static final Logger log = LoggerFactory.getLogger(AppleWalletController.class);

    private final WalletService walletService;

    public AppleWalletController(@Qualifier("appleWalletService") WalletService walletService) {
        this.walletService = walletService;
    }

    /** Apple Wallet calls this when the user adds the pass to their device. */
    @PostMapping("/v1/devices/{deviceLibraryId}/registrations/{passTypeId}/{serial}")
    public ResponseEntity<Void> registerDevice(
            @PathVariable String deviceLibraryId,
            @PathVariable String passTypeId,
            @PathVariable String serial,
            @RequestBody Map<String, String> body) {
        String pushToken = body.get("pushToken");
        boolean isNew = walletService.registerDevice(deviceLibraryId, passTypeId, serial, pushToken);
        return isNew
                ? ResponseEntity.status(201).build()
                : ResponseEntity.ok().build();
    }

    /** Apple Wallet calls this when the user removes the pass from their device. */
    @DeleteMapping("/v1/devices/{deviceLibraryId}/registrations/{passTypeId}/{serial}")
    public ResponseEntity<Void> unregisterDevice(
            @PathVariable String deviceLibraryId,
            @PathVariable String passTypeId,
            @PathVariable String serial) {
        walletService.unregisterDevice(deviceLibraryId, passTypeId, serial);
        return ResponseEntity.ok().build();
    }

    /**
     * Apple Wallet calls this after receiving an APNs push to fetch the updated pass.
     * Returns the .pkpass binary with a Last-Modified header.
     */
    @GetMapping(value = "/v1/passes/{passTypeId}/{serial}",
                produces = "application/vnd.apple.pkpass")
    public ResponseEntity<byte[]> getPass(
            @PathVariable String passTypeId,
            @PathVariable String serial) {
        byte[] passBytes = walletService.getPassData(passTypeId, serial);
        return ResponseEntity.ok()
                .header("Last-Modified", Instant.now().toString())
                .contentType(MediaType.parseMediaType("application/vnd.apple.pkpass"))
                .body(passBytes);
    }

    /**
     * Apple Wallet calls this to check which passes need updating.
     * MUST return 204 No Content when there are no updated passes — 200 with empty array breaks Apple's client.
     */
    @GetMapping("/v1/devices/{deviceLibraryId}/registrations/{passTypeId}")
    public ResponseEntity<Map<String, Object>> getSerialNumbers(
            @PathVariable String deviceLibraryId,
            @PathVariable String passTypeId,
            @RequestParam(required = false) String passesUpdatedSince) {
        List<String> serials = walletService.getSerialNumbers(deviceLibraryId, passTypeId, passesUpdatedSince);
        if (serials.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(Map.of(
                "serialNumbers", serials,
                "lastUpdated", Instant.now().toString()
        ));
    }

    /** Apple Wallet sends diagnostic log entries here. */
    @PostMapping("/v1/log")
    public ResponseEntity<Void> log(@RequestBody Map<String, Object> body) {
        Object logs = body.get("logs");
        if (logs instanceof Iterable<?> entries) {
            for (Object entry : entries) {
                log.debug("[Apple Wallet] {}", entry);
            }
        }
        return ResponseEntity.ok().build();
    }
}
