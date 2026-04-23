package com.stampx.service;

import com.stampx.dto.StampResponseDTO;
import com.stampx.exception.NotFoundException;
import com.stampx.model.*;
import com.stampx.repository.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Service
@Transactional
public class StampService {

    private final CustomerRepository customerRepository;
    private final StampEventRepository stampEventRepository;
    private final RewardRepository rewardRepository;
    private final ShopRepository shopRepository;
    private final WalletService walletService;

    public StampService(CustomerRepository customerRepository,
                        StampEventRepository stampEventRepository,
                        RewardRepository rewardRepository,
                        ShopRepository shopRepository,
                        @Qualifier("appleWalletService") WalletService walletService) {
        this.customerRepository = customerRepository;
        this.stampEventRepository = stampEventRepository;
        this.rewardRepository = rewardRepository;
        this.shopRepository = shopRepository;
        this.walletService = walletService;
    }

    /**
     * Records a stamp for the customer identified by passSerial, scoped to the authenticated shop.
     * If the customer reaches the stamp goal, a Reward is created and stamp_count is reset to 0.
     * The APNs push is sent AFTER the transaction commits to avoid holding a DB connection
     * open during an HTTP call to the pass-service.
     */
    public StampResponseDTO stampCustomer(UUID shopId, String passSerial) {
        Customer customer = customerRepository.findByPassSerialAndShopId(passSerial, shopId)
                .orElseThrow(() -> new NotFoundException("Pass not found for this shop: " + passSerial));

        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new NotFoundException("Shop not found"));

        customer.setStampCount(customer.getStampCount() + 1);

        StampEvent event = new StampEvent();
        event.setShop(shop);
        event.setCustomer(customer);
        stampEventRepository.save(event);

        boolean rewardEarned = customer.getStampCount() >= shop.getStampGoal();
        UUID rewardId = null;

        if (rewardEarned) {
            Reward reward = new Reward();
            reward.setShop(shop);
            reward.setCustomer(customer);
            rewardRepository.save(reward);
            rewardId = reward.getId();

            customer.setStampCount(0);
        }

        customerRepository.save(customer);

        // Capture final state for use in the after-commit callback
        final Customer savedCustomer = customer;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                walletService.sendPushNotification(savedCustomer);
            }
        });

        return new StampResponseDTO(
                customer.getId(),
                customer.getPassSerial(),
                customer.getStampCount(),
                shop.getStampGoal(),
                rewardEarned,
                rewardId
        );
    }
}
