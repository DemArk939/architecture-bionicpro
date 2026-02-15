package com.example.bionicproauth.controller;

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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@Slf4j
@RestController
public class AuthCallbackController {

    private final KeycloakService keycloakService;
    private final TokenEncryptionService encryptionService;
    private final String clientId;
    private final String redirectUri;

    public AuthCallbackController(KeycloakService keycloakService,
                                  TokenEncryptionService encryptionService,
                                  @Value("${keycloak.client-id}") String clientId,
                                  @Value("${app.redirect-uri}") String redirectUri) {
        this.keycloakService = keycloakService;
        this.encryptionService = encryptionService;
        this.clientId = clientId;
        this.redirectUri = redirectUri;
    }

    @PostMapping("/auth/callback")
    public ResponseEntity<?> callback(@RequestBody Map<String, String> payload, HttpServletRequest request) {
        String code = payload.get("code");
        String codeVerifier = payload.get("codeVerifier");

        if (code == null || codeVerifier == null) {
            return ResponseEntity.badRequest().body("Missing code or verifier");
        }

        try {
            // Обмениваем код на токены
            TokenResponse tokens = keycloakService.exchangeCodeForTokens(code, codeVerifier, clientId, redirectUri);

            HttpSession session = request.getSession(true);
            request.changeSessionId(); // ротация session id

            String encryptedRefresh = encryptionService.encrypt(tokens.getRefreshToken());

            session.setAttribute("username", "user"); // можно извлечь из токена
            session.setAttribute("accessToken", tokens.getAccessToken());
            session.setAttribute("refreshToken", encryptedRefresh);
            long expiresAt = Instant.now().plusSeconds(tokens.getExpiresIn()).toEpochMilli();
            session.setAttribute("expiresAt", expiresAt);

            var auth = SecurityContextUtils.createAuthentication("user", tokens);
            SecurityContextHolder.getContext().setAuthentication(auth);

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Token exchange failed", e);
            return ResponseEntity.status(401).body("Authentication failed");
        }
    }
}