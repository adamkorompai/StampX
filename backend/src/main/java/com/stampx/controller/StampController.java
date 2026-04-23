package com.stampx.controller;

import com.stampx.dto.StampResponseDTO;
import com.stampx.model.Shop;
import com.stampx.service.StampService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stamp")
public class StampController {

    private final StampService stampService;

    public StampController(StampService stampService) {
        this.stampService = stampService;
    }

    /**
     * Records a stamp for the customer identified by passSerial.
     * Authentication is handled upstream by ApiKeyAuthFilter — the principal
     * in the SecurityContext is the authenticated Shop entity.
     */
    @PostMapping("/{passSerial}")
    public StampResponseDTO stamp(@PathVariable String passSerial) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Shop shop = (Shop) auth.getPrincipal();
        return stampService.stampCustomer(shop.getId(), passSerial);
    }
}
