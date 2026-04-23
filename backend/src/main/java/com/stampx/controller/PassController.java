package com.stampx.controller;

import com.stampx.dto.ShopPublicDTO;
import com.stampx.exception.NotFoundException;
import com.stampx.model.Customer;
import com.stampx.model.Shop;
import com.stampx.repository.ShopRepository;
import com.stampx.service.CustomerService;
import com.stampx.service.PassService;
import com.stampx.service.ShopService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class PassController {

    private final ShopRepository shopRepository;
    private final PassService passService;
    private final CustomerService customerService;

    public PassController(ShopRepository shopRepository,
                          PassService passService,
                          CustomerService customerService) {
        this.shopRepository = shopRepository;
        this.passService = passService;
        this.customerService = customerService;
    }

    @GetMapping("/shops/{slug}")
    public ShopPublicDTO shopInfo(@PathVariable String slug) {
        Shop shop = shopRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Shop not found: " + slug));
        return ShopService.toPublicDTO(shop);
    }

    /**
     * Generates a new .pkpass file for a first-time visitor, creating a Customer row.
     * Returning customers use their existing pass — they don't hit this endpoint again.
     */
    @GetMapping(value = "/pass/download/{slug}", produces = "application/vnd.apple.pkpass")
    public ResponseEntity<byte[]> downloadPass(@PathVariable String slug) {
        Shop shop = shopRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Shop not found: " + slug));

        String passSerial = UUID.randomUUID().toString();
        byte[] passBytes = passService.generatePass(shop, passSerial, 0);

        // Persist the customer record after generating the pass successfully
        customerService.createCustomer(shop, passSerial);

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"loyalty.pkpass\"")
                .contentType(MediaType.parseMediaType("application/vnd.apple.pkpass"))
                .body(passBytes);
    }
}
