package com.stampx.service;

import com.stampx.model.Shop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class PassService {

    private static final Logger log = LoggerFactory.getLogger(PassService.class);

    private final RestTemplate restTemplate;

    @Value("${app.pass-service-url}")
    private String passServiceUrl;

    public PassService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Calls the Node.js pass-service to generate a .pkpass binary for a customer.
     */
    public byte[] generatePass(Shop shop, String serialNumber, int stampCount) {
        Map<String, Object> body = Map.of(
                "shopName", shop.getName(),
                "logoUrl", shop.getLogoUrl() != null ? shop.getLogoUrl() : "",
                "primaryColor", shop.getPrimaryColor() != null ? shop.getPrimaryColor() : "#000000",
                "stampCount", stampCount,
                "stampGoal", shop.getStampGoal(),
                "serialNumber", serialNumber,
                "rewardDescription", shop.getRewardDescription() != null ? shop.getRewardDescription() : ""
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<byte[]> response = restTemplate.exchange(
                passServiceUrl + "/generate",
                HttpMethod.POST,
                request,
                byte[].class
        );

        if (response.getBody() == null) {
            throw new RuntimeException("pass-service returned empty body");
        }
        return response.getBody();
    }

    /**
     * Sends an APNs silent push so Apple Wallet fetches the updated pass.
     * Errors are logged but not propagated — a failed push is not fatal.
     */
    public void sendPushNotification(String pushToken, String passTypeIdentifier) {
        try {
            Map<String, String> body = Map.of(
                    "pushToken", pushToken,
                    "passTypeIdentifier", passTypeIdentifier
            );
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForEntity(
                    passServiceUrl + "/push",
                    new HttpEntity<>(body, headers),
                    Void.class
            );
        } catch (Exception e) {
            log.warn("APNs push failed for token {}: {}", pushToken, e.getMessage());
        }
    }
}
