package com.stampx.service;

import com.stampx.model.Customer;

import java.util.List;

/**
 * Abstraction over wallet platform services (Apple Wallet, Google Wallet, etc.).
 * Controllers depend on this interface so adding AndroidWalletService requires
 * only a new implementation + a new controller — no changes to existing code.
 */
public interface WalletService {

    /**
     * Registers a device for push updates.
     * @return true if this is a new registration, false if already registered
     */
    boolean registerDevice(String deviceLibraryId, String passTypeId, String serial, String pushToken);

    void unregisterDevice(String deviceLibraryId, String passTypeId, String serial);

    /** Returns the current .pkpass (or equivalent) bytes for the given serial. */
    byte[] getPassData(String passTypeId, String serial);

    /**
     * Returns serials of passes that have been updated since the given ISO-8601 timestamp.
     * Pass null to return all serials for the device.
     */
    List<String> getSerialNumbers(String deviceLibraryId, String passTypeId, String passesUpdatedSince);

    /** Sends a silent push so the wallet client fetches the updated pass. */
    void sendPushNotification(Customer customer);
}
