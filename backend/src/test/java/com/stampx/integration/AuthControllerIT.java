package com.stampx.integration;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerIT extends BaseIntegrationTest {

    // ── POST /api/auth/register ───────────────────────────────────────────────

    @Test
    void register_validPayload_returns201WithApiKey() throws Exception {
        var response = registerShop("My Cafe", "my-cafe", "cafe@test.com", "password123");

        assertThat(response).containsKeys("id", "name", "slug", "email", "apiKey");
        assertThat(response.get("name")).isEqualTo("My Cafe");
        assertThat(response.get("slug")).isEqualTo("my-cafe");
        // apiKey must be present and non-blank (it is the raw UUID, returned only once)
        assertThat((String) response.get("apiKey")).isNotBlank();
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        registerShop("Shop A", "shop-a", "dup@test.com", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Shop B","slug":"shop-b","email":"dup@test.com",
                                 "password":"password123","stampGoal":5,
                                 "rewardDescription":"Reward","primaryColor":"#000000","logoUrl":""}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void register_duplicateSlug_returns409() throws Exception {
        registerShop("Shop A", "slug-taken", "a@test.com", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Shop B","slug":"slug-taken","email":"b@test.com",
                                 "password":"password123","stampGoal":5,
                                 "rewardDescription":"Reward","primaryColor":"#000000","logoUrl":""}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void register_invalidEmail_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Shop","slug":"s","email":"not-an-email",
                                 "password":"pass123","stampGoal":5,
                                 "rewardDescription":"r","primaryColor":"#000000","logoUrl":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void register_passwordTooShort_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Shop","slug":"short-pw","email":"x@test.com",
                                 "password":"short","stampGoal":5,
                                 "rewardDescription":"r","primaryColor":"#000000","logoUrl":""}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/auth/login ──────────────────────────────────────────────────

    @Test
    void login_correctCredentials_returns200AndSetsSession() throws Exception {
        registerShop("Login Shop", "login-shop", "login@test.com", "password123");

        HttpSession session = loginAndGetSession("login@test.com", "password123");

        assertThat(session).isNotNull();
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        registerShop("Bad Pass Shop", "bad-pass", "badpass@test.com", "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"badpass@test.com","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_unknownEmail_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nobody@test.com","password":"password123"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/auth/me ──────────────────────────────────────────────────────

    @Test
    void me_withValidSession_returnsShopInfo() throws Exception {
        registerShop("Me Shop", "me-shop", "me@test.com", "password123");
        HttpSession session = loginAndGetSession("me@test.com", "password123");

        mockMvc.perform(get("/api/auth/me").session((MockHttpSession) session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Me Shop"))
                .andExpect(jsonPath("$.slug").value("me-shop"));
    }

    @Test
    void me_withoutSession_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/auth/logout ─────────────────────────────────────────────────

    @Test
    void logout_invalidatesSession() throws Exception {
        registerShop("Logout Shop", "logout-shop", "logout@test.com", "password123");
        HttpSession session = loginAndGetSession("logout@test.com", "password123");

        // Logout
        mockMvc.perform(post("/api/auth/logout").session((MockHttpSession) session))
                .andExpect(status().isOk());

        // Subsequent /me with the same session must fail
        mockMvc.perform(get("/api/auth/me").session((MockHttpSession) session))
                .andExpect(status().isUnauthorized());
    }
}
