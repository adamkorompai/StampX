package com.stampx.config;

import com.stampx.model.Shop;
import com.stampx.repository.ShopRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthFilterTest {

    @Mock ShopRepository shopRepository;

    ApiKeyAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthFilter(shopRepository);
        SecurityContextHolder.clearContext();
    }

    // ── non-stamp URL ─────────────────────────────────────────────────────────

    @Test
    void nonStampUrl_passesThrough_noAuthCheck() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/dashboard/stats");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        // Filter chain must continue and no repository lookup must happen
        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(shopRepository);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    // ── missing header ────────────────────────────────────────────────────────

    @Test
    void stampUrl_missingHeader_returns401() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/stamp/some-serial");
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(shopRepository);
        verifyNoInteractions(chain);
    }

    // ── invalid key ───────────────────────────────────────────────────────────

    @Test
    void stampUrl_invalidKey_returns401() throws Exception {
        when(shopRepository.findByApiKey(anyString())).thenReturn(Optional.empty());

        var request = new MockHttpServletRequest("POST", "/api/stamp/some-serial");
        request.addHeader("X-Api-Key", "invalid-key");
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    // ── valid key ─────────────────────────────────────────────────────────────

    @Test
    void stampUrl_validKey_setsAuthenticationAndContinues() throws Exception {
        String rawKey = UUID.randomUUID().toString();
        String hash = ApiKeyAuthFilter.sha256Hex(rawKey);

        Shop shop = new Shop();
        shop.setId(UUID.randomUUID());
        shop.setName("Test Shop");
        when(shopRepository.findByApiKey(hash)).thenReturn(Optional.of(shop));

        var request = new MockHttpServletRequest("POST", "/api/stamp/serial-xyz");
        request.addHeader("X-Api-Key", rawKey);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        // Chain must have continued
        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);

        // Authentication must be set in SecurityContext
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.isAuthenticated()).isTrue();
        assertThat(auth.getPrincipal()).isSameAs(shop);
        assertThat(auth.getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_SHOP"));
    }

    // ── sha256Hex ─────────────────────────────────────────────────────────────

    @Test
    void sha256Hex_samInputProducesSameHash() {
        String key = "test-key-123";
        assertThat(ApiKeyAuthFilter.sha256Hex(key)).isEqualTo(ApiKeyAuthFilter.sha256Hex(key));
    }

    @Test
    void sha256Hex_differentInputsProduceDifferentHashes() {
        assertThat(ApiKeyAuthFilter.sha256Hex("key-a"))
                .isNotEqualTo(ApiKeyAuthFilter.sha256Hex("key-b"));
    }

    @Test
    void sha256Hex_outputIsLowercaseHex64Chars() {
        String hash = ApiKeyAuthFilter.sha256Hex("any-value");
        assertThat(hash).hasSize(64).matches("[0-9a-f]+");
    }
}
