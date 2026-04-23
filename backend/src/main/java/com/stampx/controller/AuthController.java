package com.stampx.controller;

import com.stampx.dto.LoginDTO;
import com.stampx.dto.ShopPublicDTO;
import com.stampx.dto.ShopRegistrationDTO;
import com.stampx.model.Shop;
import com.stampx.service.ShopService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final ShopService shopService;
    private final SecurityContextRepository securityContextRepository;

    public AuthController(ShopService shopService, SecurityContextRepository securityContextRepository) {
        this.shopService = shopService;
        this.securityContextRepository = securityContextRepository;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(@RequestBody @Valid ShopRegistrationDTO dto) {
        ShopService.RegistrationResult result = shopService.registerShop(dto);
        Shop shop = result.shop();
        return Map.of(
                "id", shop.getId(),
                "name", shop.getName(),
                "slug", shop.getSlug(),
                "email", shop.getEmail(),
                "stampGoal", shop.getStampGoal(),
                "apiKey", result.rawApiKey()  // returned ONCE — store it safely
        );
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody @Valid LoginDTO dto,
                                                      HttpServletRequest request,
                                                      HttpServletResponse response) {
        Shop shop = shopService.authenticateShop(dto.email(), dto.password());

        Authentication auth = new UsernamePasswordAuthenticationToken(
                shop, null,
                List.of(new SimpleGrantedAuthority("ROLE_SHOP"))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        // Explicitly persist the security context to the session (required in Spring Boot 3.2)
        securityContextRepository.saveContext(context, request, response);

        return ResponseEntity.ok(Map.of(
                "id", shop.getId(),
                "name", shop.getName(),
                "slug", shop.getSlug(),
                "email", shop.getEmail()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<ShopPublicDTO> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof Shop shop)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(ShopService.toPublicDTO(shop));
    }
}
