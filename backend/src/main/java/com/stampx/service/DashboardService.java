package com.stampx.service;

import com.stampx.dto.DashboardStatsDTO;
import com.stampx.dto.RewardDTO;
import com.stampx.exception.ConflictException;
import com.stampx.exception.NotFoundException;
import com.stampx.repository.CustomerRepository;
import com.stampx.repository.RewardRepository;
import com.stampx.repository.StampEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class DashboardService {

    private final CustomerRepository customerRepository;
    private final StampEventRepository stampEventRepository;
    private final RewardRepository rewardRepository;

    public DashboardService(CustomerRepository customerRepository,
                             StampEventRepository stampEventRepository,
                             RewardRepository rewardRepository) {
        this.customerRepository = customerRepository;
        this.stampEventRepository = stampEventRepository;
        this.rewardRepository = rewardRepository;
    }

    @Transactional(readOnly = true)
    public DashboardStatsDTO getStats(UUID shopId) {
        long totalCustomers = customerRepository.countByShopId(shopId);

        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endOfDay = startOfDay.plus(Duration.ofDays(1));
        long stampsToday = stampEventRepository.countByShopIdAndStampedAtBetween(shopId, startOfDay, endOfDay);

        long pendingRewards = rewardRepository.countByShopIdAndRedeemedAtIsNull(shopId);

        return new DashboardStatsDTO(totalCustomers, stampsToday, pendingRewards);
    }

    @Transactional(readOnly = true)
    public List<RewardDTO> getPendingRewards(UUID shopId) {
        return rewardRepository.findAllByShopIdAndRedeemedAtIsNull(shopId)
                .stream()
                .map(r -> new RewardDTO(
                        r.getId(),
                        r.getCustomer().getId(),
                        r.getCustomer().getPassSerial(),
                        r.getCustomer().getStampCount(),
                        r.getCustomer().getCreatedAt(),
                        r.getRedeemedAt()
                ))
                .toList();
    }

    public void redeemReward(UUID shopId, UUID rewardId) {
        var reward = rewardRepository.findByIdAndShopId(rewardId, shopId)
                .orElseThrow(() -> new NotFoundException("Reward not found"));
        if (reward.getRedeemedAt() != null) {
            throw new ConflictException("Reward already redeemed");
        }
        reward.setRedeemedAt(Instant.now());
        rewardRepository.save(reward);
    }
}
