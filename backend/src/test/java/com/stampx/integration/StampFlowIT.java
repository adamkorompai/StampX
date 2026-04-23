package com.stampx.integration;

import com.stampx.config.ApiKeyAuthFilter;
import com.stampx.model.Customer;
import com.stampx.model.Shop;
import com.stampx.repository.CustomerRepository;
import com.stampx.repository.ShopRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end stamp flow test:
 * register shop → seed customer → stamp → check dashboard → redeem reward
 *
 * PassService is mocked in BaseIntegrationTest — no Node.js pass-service required.
 */
class StampFlowIT extends BaseIntegrationTest {

    @Autowired ShopRepository shopRepository;
    @Autowired CustomerRepository customerRepository;

    String rawApiKey;
    UUID shopId;
    String passSerial;
    HttpSession session;

    @BeforeEach
    void setUpShopAndCustomer() throws Exception {
        // Use a unique slug/email per test run to avoid inter-test conflicts
        String uid = UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> reg = registerShop(
                "Stamp Shop", "stamp-" + uid, uid + "@test.com", "password123");
        rawApiKey = (String) reg.get("apiKey");
        shopId = UUID.fromString((String) reg.get("id"));

        session = loginAndGetSession(uid + "@test.com", "password123");

        // Seed a customer directly — bypass pass download (which calls pass-service)
        Shop shop = shopRepository.findById(shopId).orElseThrow();
        passSerial = "test-serial-" + UUID.randomUUID();
        Customer customer = new Customer();
        customer.setShop(shop);
        customer.setPassSerial(passSerial);
        customer.setStampCount(0);
        customerRepository.save(customer);
    }

    // ── single stamp below goal ───────────────────────────────────────────────

    @Test
    void stamp_belowGoal_incrementsCountAndNoReward() throws Exception {
        mockMvc.perform(post("/api/stamp/" + passSerial)
                        .header("X-Api-Key", rawApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stampCount").value(1))
                .andExpect(jsonPath("$.stampGoal").value(5))
                .andExpect(jsonPath("$.rewardEarned").value(false))
                .andExpect(jsonPath("$.rewardId").doesNotExist());
    }

    // ── stamp with wrong API key ──────────────────────────────────────────────

    @Test
    void stamp_wrongApiKey_returns401() throws Exception {
        mockMvc.perform(post("/api/stamp/" + passSerial)
                        .header("X-Api-Key", "completely-wrong-key"))
                .andExpect(status().isUnauthorized());
    }

    // ── stamp with missing API key ────────────────────────────────────────────

    @Test
    void stamp_missingApiKey_returns401() throws Exception {
        mockMvc.perform(post("/api/stamp/" + passSerial))
                .andExpect(status().isUnauthorized());
    }

    // ── stamp unknown serial ──────────────────────────────────────────────────

    @Test
    void stamp_unknownSerial_returns404() throws Exception {
        mockMvc.perform(post("/api/stamp/does-not-exist")
                        .header("X-Api-Key", rawApiKey))
                .andExpect(status().isNotFound());
    }

    // ── full reward cycle ─────────────────────────────────────────────────────

    @Test
    void fullRewardCycle_stampUntilGoal_createsAndRedeemsReward() throws Exception {
        // Stamp 4 times — still below goal (goal = 5)
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/stamp/" + passSerial)
                            .header("X-Api-Key", rawApiKey))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rewardEarned").value(false));
        }

        // 5th stamp hits the goal
        var result = mockMvc.perform(post("/api/stamp/" + passSerial)
                        .header("X-Api-Key", rawApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rewardEarned").value(true))
                .andExpect(jsonPath("$.stampCount").value(0))    // reset after reward
                .andExpect(jsonPath("$.rewardId").isNotEmpty())
                .andReturn();

        String rewardId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("rewardId").asText();

        // Dashboard stats must show 1 pending reward
        mockMvc.perform(get("/api/dashboard/stats").session((MockHttpSession) session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingRewardsCount").value(1));

        // Pending rewards list must contain the reward
        mockMvc.perform(get("/api/dashboard/rewards/pending").session((MockHttpSession) session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(rewardId));

        // Redeem the reward
        mockMvc.perform(post("/api/dashboard/rewards/" + rewardId + "/redeem")
                        .session((MockHttpSession) session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Redeemed"));

        // Pending count must now be 0
        mockMvc.perform(get("/api/dashboard/stats").session((MockHttpSession) session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingRewardsCount").value(0));
    }

    // ── double redeem ─────────────────────────────────────────────────────────

    @Test
    void redeemReward_twice_returns409() throws Exception {
        // Stamp to goal
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/stamp/" + passSerial)
                            .header("X-Api-Key", rawApiKey))
                    .andExpect(status().isOk());
        }

        var pendingResult = mockMvc.perform(get("/api/dashboard/rewards/pending")
                        .session((MockHttpSession) session))
                .andExpect(status().isOk())
                .andReturn();

        String rewardId = objectMapper.readTree(pendingResult.getResponse().getContentAsString())
                .get(0).get("id").asText();

        // First redeem succeeds
        mockMvc.perform(post("/api/dashboard/rewards/" + rewardId + "/redeem")
                        .session((MockHttpSession) session))
                .andExpect(status().isOk());

        // Second redeem must fail with 409
        mockMvc.perform(post("/api/dashboard/rewards/" + rewardId + "/redeem")
                        .session((MockHttpSession) session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    // ── dashboard requires auth ───────────────────────────────────────────────

    @Test
    void dashboardStats_withoutSession_returns401() throws Exception {
        mockMvc.perform(get("/api/dashboard/stats"))
                .andExpect(status().isUnauthorized());
    }

    // ── public shop endpoint ──────────────────────────────────────────────────

    @Test
    void shopInfo_publicEndpoint_noAuthRequired() throws Exception {
        Shop shop = shopRepository.findById(shopId).orElseThrow();
        mockMvc.perform(get("/api/shops/" + shop.getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Stamp Shop"))
                .andExpect(jsonPath("$.stampGoal").value(5));
    }

    @Test
    void shopInfo_unknownSlug_returns404() throws Exception {
        mockMvc.perform(get("/api/shops/does-not-exist"))
                .andExpect(status().isNotFound());
    }
}
