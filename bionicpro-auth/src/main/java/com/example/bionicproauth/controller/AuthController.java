package com.example.bionicproauth.controller;

import com.example.bionicproauth.model.AuthStatus;
import com.example.bionicproauth.model.LoginRequest;
import com.example.bionicproauth.model.TokenResponse;
import com.example.bionicproauth.service.KeycloakService;
import com.example.bionicproauth.service.TokenEncryptionService;
import com.example.bionicproauth.util.SecurityContextUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@Slf4j
@RestController
public class AuthController {

    private final KeycloakService keycloakService;
    private final TokenEncryptionService encryptionService;
    private final String clientId;

    public AuthController(KeycloakService keycloakService,
                          TokenEncryptionService encryptionService,
                          @Value("${keycloak.client-id}") String clientId) {
        this.keycloakService = keycloakService;
        this.encryptionService = encryptionService;
        this.clientId = clientId;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        try {
            TokenResponse tokens = keycloakService.getTokens(loginRequest.getUsername(), loginRequest.getPassword(), clientId);
            log.info("Для пользователя {} получен токен {}, RefreshToken {}",
                    loginRequest.getUsername(),
                    tokens.getAccessToken(),
                    tokens.getRefreshToken());
            HttpSession session = request.getSession(true);
            request.changeSessionId(); // ротация session id

            String encryptedRefresh = encryptionService.encrypt(tokens.getRefreshToken());

            session.setAttribute("username", loginRequest.getUsername());
            session.setAttribute("accessToken", tokens.getAccessToken());
            session.setAttribute("refreshToken", encryptedRefresh);
            long expiresAt = Instant.now().plusSeconds(tokens.getExpiresIn()).toEpochMilli();
            session.setAttribute("expiresAt", expiresAt);

            var auth = SecurityContextUtils.createAuthentication(loginRequest.getUsername(), tokens);
            SecurityContextHolder.getContext().setAuthentication(auth);

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(401).body("Login failed");
        }
    }

    @GetMapping("/status")
    public ResponseEntity<AuthStatus> status(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        boolean authenticated = session != null && session.getAttribute("accessToken") != null;
        return ResponseEntity.ok(new AuthStatus(authenticated));
    }
}