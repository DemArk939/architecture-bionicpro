package com.example.bionicproauth.filter;

import com.example.bionicproauth.model.TokenResponse;
import com.example.bionicproauth.service.KeycloakService;
import com.example.bionicproauth.service.TokenEncryptionService;
import com.example.bionicproauth.util.SecurityContextUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;

@Slf4j
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final KeycloakService keycloakService;
    private final TokenEncryptionService encryptionService;
    private final String clientId;

    public TokenAuthenticationFilter(KeycloakService keycloakService,
                                     TokenEncryptionService encryptionService,
                                     @Value("${keycloak.client-id}") String clientId) {
        this.keycloakService = keycloakService;
        this.encryptionService = encryptionService;
        this.clientId = clientId;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            log.info("Проверка токена для сессии {}", session.getId());
            // Извлекаем зашифрованный refresh token и access token из сессии
            String encryptedRefreshToken = (String) session.getAttribute("refreshToken");
            String accessToken = (String) session.getAttribute("accessToken");
            Long expiresAt = (Long) session.getAttribute("expiresAt");

            log.info("Токен {}, время жизни {}", session.getId(), new Date(expiresAt));

            if (accessToken != null && encryptedRefreshToken != null) {
                // Проверяем, не истёк ли access token
                if (expiresAt != null && Instant.now().toEpochMilli() > expiresAt) {
                    // Нужно обновить токены
                    try {
                        log.info("Обновляем токен");
                        String refreshToken = encryptionService.decrypt(encryptedRefreshToken);

                        log.info("refreshToken {}", refreshToken);
                        TokenResponse newTokens = keycloakService.refreshTokens(refreshToken, clientId);

                        log.info("newTokens AccessToken {} , RefreshToken {}", newTokens.getAccessToken(), newTokens.getRefreshToken());
                        // Сохраняем новые токены
                        session.setAttribute("accessToken", newTokens.getAccessToken());
                        String newEncryptedRefresh = encryptionService.encrypt(newTokens.getRefreshToken());
                        session.setAttribute("refreshToken", newEncryptedRefresh);
                        long newExpiresAt = Instant.now().plusSeconds(newTokens.getExpiresIn()).toEpochMilli();
                        session.setAttribute("expiresAt", newExpiresAt);

                        String oldSessionId = session.getId();

                        // Ротация session id (предотвращение session fixation)
                        request.changeSessionId();

                        log.info("Ротация для сессии {}, новый ИД сессии {}", oldSessionId, request.getSession(false).getId());

                        accessToken = newTokens.getAccessToken();
                    } catch (Exception e) {
                        // Ошибка обновления — удаляем сессию и перенаправляем на логин
                        session.invalidate();
                        SecurityContextHolder.clearContext();
                        chain.doFilter(request, response);
                        return;
                    }
                }

                // Устанавливаем аутентификацию в SecurityContext
                String username = (String) session.getAttribute("username");
                if (username != null) {
                    TokenResponse fakeTokens = new TokenResponse(); // Для создания Authentication используем заглушку
                    fakeTokens.setAccessToken(accessToken);
                    Authentication auth = SecurityContextUtils.createAuthentication(username, fakeTokens);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }

        chain.doFilter(request, response);
    }
}