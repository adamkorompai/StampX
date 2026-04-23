package com.stampx.repository;

import com.stampx.model.Reward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RewardRepository extends JpaRepository<Reward, UUID> {
    List<Reward> findAllByShopIdAndRedeemedAtIsNull(UUID shopId);
    long countByShopIdAndRedeemedAtIsNull(UUID shopId);
    Optional<Reward> findByIdAndShopId(UUID id, UUID shopId);
}
