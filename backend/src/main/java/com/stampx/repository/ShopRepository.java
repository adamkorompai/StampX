package com.stampx.repository;

import com.stampx.model.Shop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ShopRepository extends JpaRepository<Shop, UUID> {
    Optional<Shop> findBySlug(String slug);
    Optional<Shop> findByEmail(String email);
    Optional<Shop> findByApiKey(String apiKeyHash);
    boolean existsBySlug(String slug);
    boolean existsByEmail(String email);
}
