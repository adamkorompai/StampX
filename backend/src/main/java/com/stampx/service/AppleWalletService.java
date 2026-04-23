package com.stampx.service;

import com.stampx.exception.NotFoundException;
import com.stampx.model.Customer;
import com.stampx.repository.CustomerRepository;
import com.stampx.repository.ShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Apple Wallet implementation of WalletService.
 * Handles device registration, pass delivery, and APNs push for .pkpass files.
 * Qualified as "appleWalletService" so AppleWalletController can inject it explicitly,
 * leaving room for AndroidWalletService to be added later under a different qualifier.
 */
@Service("appleWalletService")
@Transactional
public class AppleWalletService implements WalletService {

    private static final Logger log = LoggerFactory.getLogger(AppleWalletService.class);

    private final CustomerRepository customerRepository;
    private final ShopRepository shopRepository;
    private final PassService passService;

    @Value("${app.apple-pass-type-identifier}")
    private String passTypeIdentifier;

    public AppleWalletService(CustomerRepository customerRepository,
                               ShopRepository shopRepository,
                               PassService passService) {
        this.customerRepository = customerRepository;
        this.shopRepository = shopRepository;
        this.passService = passService;
    }

    /**
     * Stores the device library ID and push token on the customer record.
     * @return true if this is the first registration for this device+serial pair
     */
    @Override
    public boolean registerDevice(String deviceLibraryId, String passTypeId, String serial, String pushToken) {
        Customer customer = customerRepository.findByPassSerial(serial)
                .orElseThrow(() -> new NotFoundException("Pass not found: " + serial));

        boolean isNew = customer.getDeviceLibraryId() == null
                || !customer.getDeviceLibraryId().equals(deviceLibraryId);

        customer.setDeviceLibraryId(deviceLibraryId);
        customer.setPushToken(pushToken);
        customerRepository.save(customer);
        return isNew;
    }

    @Override
    public void unregisterDevice(String deviceLibraryId, String passTypeId, String serial) {
        customerRepository.findByDeviceLibraryIdAndPassSerial(deviceLibraryId, serial)
                .ifPresent(customer -> {
                    customer.setDeviceLibraryId(null);
                    customer.setPushToken(null);
                    customerRepository.save(customer);
                });
    }

    /**
     * Regenerates and returns the latest .pkpass bytes for the given serial.
     * Apple Wallet calls this after receiving a silent push notification.
     */
    @Override
    @Transactional(readOnly = true)
    public byte[] getPassData(String passTypeId, String serial) {
        Customer customer = customerRepository.findByPassSerial(serial)
                .orElseThrow(() -> new NotFoundException("Pass not found: " + serial));
        return passService.generatePass(customer.getShop(), serial, customer.getStampCount());
    }

    /**
     * Returns all pass serials registered on the given device.
     * Simplified implementation: ignores passesUpdatedSince and returns all serials.
     * A production implementation would track a lastUpdated timestamp per customer.
     */
    @Override
    @Transactional(readOnly = true)
    public List<String> getSerialNumbers(String deviceLibraryId, String passTypeId, String passesUpdatedSince) {
        return customerRepository
                .findAll()
                .stream()
                .filter(c -> deviceLibraryId.equals(c.getDeviceLibraryId()))
                .map(Customer::getPassSerial)
                .toList();
    }

    @Override
    public void sendPushNotification(Customer customer) {
        if (customer.getPushToken() == null || customer.getPushToken().isBlank()) {
            log.debug("No push token for customer {}, skipping APNs push", customer.getId());
            return;
        }
        passService.sendPushNotification(customer.getPushToken(), passTypeIdentifier);
    }
}
