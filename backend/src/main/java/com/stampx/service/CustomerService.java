package com.stampx.service;

import com.stampx.exception.NotFoundException;
import com.stampx.model.Customer;
import com.stampx.model.Shop;
import com.stampx.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public Customer findByPassSerial(String passSerial) {
        return customerRepository.findByPassSerial(passSerial)
                .orElseThrow(() -> new NotFoundException("Customer not found for serial: " + passSerial));
    }

    public Customer createCustomer(Shop shop, String passSerial) {
        Customer customer = new Customer();
        customer.setShop(shop);
        customer.setPassSerial(passSerial);
        customer.setStampCount(0);
        return customerRepository.save(customer);
    }

    public void updateDeviceRegistration(String passSerial, String deviceLibraryId, String pushToken) {
        Customer customer = customerRepository.findByPassSerial(passSerial)
                .orElseThrow(() -> new NotFoundException("Customer not found for serial: " + passSerial));
        customer.setDeviceLibraryId(deviceLibraryId);
        customer.setPushToken(pushToken);
        customerRepository.save(customer);
    }

    public boolean removeDeviceRegistration(String passSerial, String deviceLibraryId) {
        return customerRepository.findByDeviceLibraryIdAndPassSerial(deviceLibraryId, passSerial)
                .map(customer -> {
                    customer.setDeviceLibraryId(null);
                    customer.setPushToken(null);
                    customerRepository.save(customer);
                    return true;
                })
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<String> getSerialNumbersForDevice(String deviceLibraryId, UUID shopId) {
        return customerRepository.findAllByDeviceLibraryIdAndShopId(deviceLibraryId, shopId)
                .stream()
                .map(Customer::getPassSerial)
                .toList();
    }
}
