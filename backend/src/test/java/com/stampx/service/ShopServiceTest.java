package com.stampx.service;

import com.stampx.config.ApiKeyAuthFilter;
import com.stampx.dto.ShopRegistrationDTO;
import com.stampx.exception.ConflictException;
import com.stampx.model.Shop;
import com.stampx.repository.ShopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShopServiceTest {

    @Mock ShopRepository shopRepository;

    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4); // low cost for tests
    ShopService shopService;

    @BeforeEach
    void setUp() {
        shopService = new ShopService(shopRepository, passwordEncoder);
    }

    // ── registerShop ──────────────────────────────────────────────────────────

    @Test
    void registerShop_success_savesShopAndReturnsRawKey() {
        when(shopRepository.existsByEmail("owner@test.com")).thenReturn(false);
        when(shopRepository.existsBySlug("my-shop")).thenReturn(false);
        when(shopRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var dto = new ShopRegistrationDTO(
                "My Shop", "my-shop", "owner@test.com", "password123",
                5, "Free coffee", "#3B82F6", null);

        ShopService.RegistrationResult result = shopService.registerShop(dto);

        // Raw key must be a non-blank UUID-format string
        assertThat(result.rawApiKey()).isNotBlank();

        // Stored api_key must be the SHA-256 hash of the raw key, not the raw key itself
        assertThat(result.shop().getApiKey())
                .isEqualTo(ApiKeyAuthFilter.sha256Hex(result.rawApiKey()))
                .isNotEqualTo(result.rawApiKey());

        // Password must be BCrypt-hashed, not stored in plain text
        assertThat(result.shop().getPasswordHash()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", result.shop().getPasswordHash())).isTrue();

        ArgumentCaptor<Shop> captor = ArgumentCaptor.forClass(Shop.class);
        verify(shopRepository).save(captor.capture());
        Shop saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("My Shop");
        assertThat(saved.getSlug()).isEqualTo("my-shop");
        assertThat(saved.getEmail()).isEqualTo("owner@test.com");
        assertThat(saved.getStampGoal()).isEqualTo(5);
    }

    @Test
    void registerShop_duplicateEmail_throwsConflict() {
        when(shopRepository.existsByEmail("dup@test.com")).thenReturn(true);

        var dto = new ShopRegistrationDTO(
                "Shop", "shop", "dup@test.com", "password123",
                5, "Reward", "#000000", null);

        assertThatThrownBy(() -> shopService.registerShop(dto))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Email");
    }

    @Test
    void registerShop_duplicateSlug_throwsConflict() {
        when(shopRepository.existsByEmail(anyString())).thenReturn(false);
        when(shopRepository.existsBySlug("taken-slug")).thenReturn(true);

        var dto = new ShopRegistrationDTO(
                "Shop", "taken-slug", "new@test.com", "password123",
                5, "Reward", "#000000", null);

        assertThatThrownBy(() -> shopService.registerShop(dto))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Slug");
    }

    // ── authenticateShop ─────────────────────────────────────────────────────

    @Test
    void authenticateShop_correctPassword_returnsShop() {
        Shop shop = shopWithHash(passwordEncoder.encode("secret"));
        when(shopRepository.findByEmail("owner@test.com")).thenReturn(Optional.of(shop));

        Shop result = shopService.authenticateShop("owner@test.com", "secret");

        assertThat(result).isSameAs(shop);
    }

    @Test
    void authenticateShop_wrongPassword_throwsBadCredentials() {
        Shop shop = shopWithHash(passwordEncoder.encode("correct"));
        when(shopRepository.findByEmail("owner@test.com")).thenReturn(Optional.of(shop));

        assertThatThrownBy(() -> shopService.authenticateShop("owner@test.com", "wrong"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void authenticateShop_unknownEmail_throwsBadCredentials() {
        when(shopRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shopService.authenticateShop("nobody@test.com", "any"))
                .isInstanceOf(BadCredentialsException.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Shop shopWithHash(String hash) {
        Shop shop = new Shop();
        shop.setId(UUID.randomUUID());
        shop.setEmail("owner@test.com");
        shop.setPasswordHash(hash);
        return shop;
    }
}
