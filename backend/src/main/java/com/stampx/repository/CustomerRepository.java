package com.stampx.repository;

import com.stampx.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByPassSerial(String passSerial);
    Optional<Customer> findByPassSerialAndShopId(String passSerial, UUID shopId);
    long countByShopId(UUID shopId);
    List<Customer> findAllByDeviceLibraryIdAndShopId(String deviceLibraryId, UUID shopId);
    Optional<Customer> findByDeviceLibraryIdAndPassSerial(String deviceLibraryId, String passSerial);
}
