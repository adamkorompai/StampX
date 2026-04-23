package com.stampx.service;

import com.stampx.dto.StampResponseDTO;
import com.stampx.exception.NotFoundException;
import com.stampx.model.*;
import com.stampx.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StampServiceTest {

    @Mock CustomerRepository customerRepository;
    @Mock StampEventRepository stampEventRepository;
    @Mock RewardRepository rewardRepository;
    @Mock ShopRepository shopRepository;
    @Mock WalletService walletService;

    StampService stampService;

    @BeforeEach
    void setUp() {
        stampService = new StampService(
                customerRepository, stampEventRepository,
                rewardRepository, shopRepository, walletService);
        // Required so TransactionSynchronizationManager.registerSynchronization() doesn't throw
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    // ── stamp below goal ──────────────────────────────────────────────────────

    @Test
    void stampCustomer_belowGoal_incrementsCountAndNoReward() {
        UUID shopId = UUID.randomUUID();
        Shop shop = shopWithGoal(shopId, 5);
        Customer customer = customerWithStamps(shopId, "serial-1", 3);

        when(customerRepository.findByPassSerialAndShopId("serial-1", shopId))
                .thenReturn(Optional.of(customer));
        when(shopRepository.findById(shopId)).thenReturn(Optional.of(shop));
        when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StampResponseDTO result = stampService.stampCustomer(shopId, "serial-1");

        assertThat(result.stampCount()).isEqualTo(4);
        assertThat(result.rewardEarned()).isFalse();
        assertThat(result.rewardId()).isNull();
        verify(rewardRepository, never()).save(any());
    }

    // ── stamp reaches goal ────────────────────────────────────────────────────

    @Test
    void stampCustomer_reachesGoal_createsRewardAndResetsCount() {
        UUID shopId = UUID.randomUUID();
        Shop shop = shopWithGoal(shopId, 5);
        Customer customer = customerWithStamps(shopId, "serial-2", 4); // one more = 5 = goal

        when(customerRepository.findByPassSerialAndShopId("serial-2", shopId))
                .thenReturn(Optional.of(customer));
        when(shopRepository.findById(shopId)).thenReturn(Optional.of(shop));
        when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(rewardRepository.save(any())).thenAnswer(inv -> {
            Reward r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        StampResponseDTO result = stampService.stampCustomer(shopId, "serial-2");

        assertThat(result.rewardEarned()).isTrue();
        assertThat(result.rewardId()).isNotNull();
        assertThat(result.stampCount()).isEqualTo(0); // reset after reward

        verify(rewardRepository).save(any(Reward.class));
        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        assertThat(captor.getValue().getStampCount()).isEqualTo(0);
    }

    // ── push is deferred to afterCommit ───────────────────────────────────────

    @Test
    void stampCustomer_pushNotCalledDuringTransaction_calledAfterCommit() {
        UUID shopId = UUID.randomUUID();
        Shop shop = shopWithGoal(shopId, 5);
        Customer customer = customerWithStamps(shopId, "serial-3", 0);

        when(customerRepository.findByPassSerialAndShopId("serial-3", shopId))
                .thenReturn(Optional.of(customer));
        when(shopRepository.findById(shopId)).thenReturn(Optional.of(shop));
        when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        stampService.stampCustomer(shopId, "serial-3");

        // Push must NOT be called synchronously inside the transaction
        verifyNoInteractions(walletService);

        // Simulate transaction commit — fire the registered afterCommit callback
        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        assertThat(syncs).isNotEmpty();
        syncs.forEach(TransactionSynchronization::afterCommit);

        // Now push should have been triggered
        verify(walletService).sendPushNotification(customer);
    }

    // ── not found ─────────────────────────────────────────────────────────────

    @Test
    void stampCustomer_unknownSerial_throwsNotFound() {
        UUID shopId = UUID.randomUUID();
        when(customerRepository.findByPassSerialAndShopId("bad-serial", shopId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> stampService.stampCustomer(shopId, "bad-serial"))
                .isInstanceOf(NotFoundException.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Shop shopWithGoal(UUID id, int goal) {
        Shop s = new Shop();
        s.setId(id);
        s.setName("Test Shop");
        s.setStampGoal(goal);
        return s;
    }

    private Customer customerWithStamps(UUID shopId, String serial, int stamps) {
        Shop shop = new Shop();
        shop.setId(shopId);
        Customer c = new Customer();
        c.setId(UUID.randomUUID());
        c.setShop(shop);
        c.setPassSerial(serial);
        c.setStampCount(stamps);
        return c;
    }
}
