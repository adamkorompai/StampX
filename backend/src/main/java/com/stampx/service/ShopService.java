package com.stampx.service;

import com.stampx.config.ApiKeyAuthFilter;
import com.stampx.dto.ShopPublicDTO;
import com.stampx.dto.ShopRegistrationDTO;
import com.stampx.exception.ConflictException;
import com.stampx.exception.NotFoundException;
import com.stampx.model.Shop;
import com.stampx.repository.ShopRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class ShopService {

    private final ShopRepository shopRepository;
    private final PasswordEncoder passwordEncoder;

    public ShopService(ShopRepository shopRepository, PasswordEncoder passwordEncoder) {
        this.shopRepository = shopRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public record RegistrationResult(Shop shop, String rawApiKey) {}

    /**
     * Creates a new shop. Returns the shop entity and the raw API key (given to the owner once).
     * The raw key is never stored; only its SHA-256 hash is persisted.
     */
    public RegistrationResult registerShop(ShopRegistrationDTO dto) {
        if (shopRepository.existsByEmail(dto.email())) {
            throw new ConflictException("Email already registered");
        }
        if (shopRepository.existsBySlug(dto.slug())) {
            throw new ConflictException("Slug already taken");
        }

        String rawApiKey = UUID.randomUUID().toString();
        String apiKeyHash = ApiKeyAuthFilter.sha256Hex(rawApiKey);

        Shop shop = new Shop();
        shop.setName(dto.name());
        shop.setSlug(dto.slug());
        shop.setEmail(dto.email());
        shop.setPasswordHash(passwordEncoder.encode(dto.password()));
        shop.setStampGoal(dto.stampGoal());
        shop.setRewardDescription(dto.rewardDescription());
        shop.setPrimaryColor(dto.primaryColor());
        shop.setLogoUrl(dto.logoUrl());
        shop.setApiKey(apiKeyHash);

        Shop saved = shopRepository.save(shop);
        return new RegistrationResult(saved, rawApiKey);
    }

    @Transactional(readOnly = true)
    public Shop authenticateShop(String email, String password) {
        Shop shop = shopRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(password, shop.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return shop;
    }

    @Transactional(readOnly = true)
    public Shop getShopById(UUID id) {
        return shopRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Shop not found"));
    }

    @Transactional(readOnly = true)
    public ShopPublicDTO getShopBySlug(String slug) {
        Shop shop = shopRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Shop not found: " + slug));
        return toPublicDTO(shop);
    }

    public static ShopPublicDTO toPublicDTO(Shop shop) {
        return new ShopPublicDTO(
                shop.getSlug(),
                shop.getName(),
                shop.getLogoUrl(),
                shop.getPrimaryColor(),
                shop.getStampGoal(),
                shop.getRewardDescription()
        );
    }
}
