package com.stampx.service;

import com.stampx.dto.DashboardStatsDTO;
import com.stampx.exception.ConflictException;
import com.stampx.exception.NotFoundException;
import com.stampx.model.*;
import com.stampx.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock CustomerRepository customerRepository;
    @Mock StampEventRepository stampEventRepository;
    @Mock RewardRepository rewardRepository;

    DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(customerRepository, stampEventRepository, rewardRepository);
    }

    // ── getStats ──────────────────────────────────────────────────────────────

    @Test
    void getStats_returnsCorrectCounts() {
        UUID shopId = UUID.randomUUID();
        when(customerRepository.countByShopId(shopId)).thenReturn(42L);
        when(stampEventRepository.countByShopIdAndStampedAtBetween(eq(shopId), any(), any())).thenReturn(7L);
        when(rewardRepository.countByShopIdAndRedeemedAtIsNull(shopId)).thenReturn(3L);

        DashboardStatsDTO stats = dashboardService.getStats(shopId);

        assertThat(stats.totalCustomers()).isEqualTo(42);
        assertThat(stats.stampsToday()).isEqualTo(7);
        assertThat(stats.pendingRewardsCount()).isEqualTo(3);
    }

    @Test
    void getStats_noActivity_returnsZeros() {
        UUID shopId = UUID.randomUUID();
        when(customerRepository.countByShopId(shopId)).thenReturn(0L);
        when(stampEventRepository.countByShopIdAndStampedAtBetween(eq(shopId), any(), any())).thenReturn(0L);
        when(rewardRepository.countByShopIdAndRedeemedAtIsNull(shopId)).thenReturn(0L);

        DashboardStatsDTO stats = dashboardService.getStats(shopId);

        assertThat(stats.totalCustomers()).isZero();
        assertThat(stats.stampsToday()).isZero();
        assertThat(stats.pendingRewardsCount()).isZero();
    }

    // ── redeemReward ──────────────────────────────────────────────────────────

    @Test
    void redeemReward_pendingReward_setsRedeemedAt() {
        UUID shopId = UUID.randomUUID();
        UUID rewardId = UUID.randomUUID();
        Reward reward = pendingReward(rewardId, shopId);

        when(rewardRepository.findByIdAndShopId(rewardId, shopId)).thenReturn(Optional.of(reward));
        when(rewardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        dashboardService.redeemReward(shopId, rewardId);

        assertThat(reward.getRedeemedAt()).isNotNull();
        assertThat(reward.getRedeemedAt()).isBeforeOrEqualTo(Instant.now());
        verify(rewardRepository).save(reward);
    }

    @Test
    void redeemReward_alreadyRedeemed_throwsConflict() {
        UUID shopId = UUID.randomUUID();
        UUID rewardId = UUID.randomUUID();
        Reward reward = pendingReward(rewardId, shopId);
        reward.setRedeemedAt(Instant.now().minusSeconds(3600));

        when(rewardRepository.findByIdAndShopId(rewardId, shopId)).thenReturn(Optional.of(reward));

        assertThatThrownBy(() -> dashboardService.redeemReward(shopId, rewardId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already redeemed");
    }

    @Test
    void redeemReward_notFound_throwsNotFound() {
        UUID shopId = UUID.randomUUID();
        UUID rewardId = UUID.randomUUID();
        when(rewardRepository.findByIdAndShopId(rewardId, shopId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dashboardService.redeemReward(shopId, rewardId))
                .isInstanceOf(NotFoundException.class);
    }

    // ── getPendingRewards ─────────────────────────────────────────────────────

    @Test
    void getPendingRewards_returnsMappedDTOs() {
        UUID shopId = UUID.randomUUID();
        Reward r1 = pendingReward(UUID.randomUUID(), shopId);
        Reward r2 = pendingReward(UUID.randomUUID(), shopId);
        when(rewardRepository.findAllByShopIdAndRedeemedAtIsNull(shopId)).thenReturn(List.of(r1, r2));

        var results = dashboardService.getPendingRewards(shopId);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(dto -> dto.redeemedAt() == null);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Reward pendingReward(UUID rewardId, UUID shopId) {
        Shop shop = new Shop();
        shop.setId(shopId);

        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setPassSerial("test-serial-" + UUID.randomUUID());
        customer.setStampCount(0);
        customer.setShop(shop);

        Reward reward = new Reward();
        reward.setId(rewardId);
        reward.setShop(shop);
        reward.setCustomer(customer);
        reward.setRedeemedAt(null);
        return reward;
    }
}
