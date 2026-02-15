package com.example.bionicproauth.service;

import com.example.bionicproauth.model.TokenResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;

@Service
public class KeycloakService {

    private final WebClient webClient;
    private final String tokenUrl;

    public KeycloakService(
            @Value("${keycloak.server-url}") String serverUrl,
            @Value("${keycloak.realm}") String realm) {
        this.tokenUrl = serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";
        this.webClient = WebClient.builder().build();
    }

    public TokenResponse getTokens(String username, String password, String clientId) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("client_id", clientId);
        body.add("username", username);
        body.add("password", password);

        return webClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(body))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .block();
    }

    public TokenResponse refreshTokens(String refreshToken, String clientId) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("client_id", clientId);
        body.add("refresh_token", refreshToken);

        return webClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(body))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .block();
    }

    // Вспомогательный метод для проверки срока токена (не требует обращения к Keycloak)
    public boolean isTokenExpired(String accessToken) {
        // Простейшая проверка по полю exp (можно через JWT парсер)
        // В реальности можно распарсить JWT и сравнить Instant.now()
        // Для примера вернём false, предполагая, что проверка будет позже.
        // Здесь можно использовать Jwts.parserBuilder()...
        return false; // Заглушка, в реальном коде нужно реализовать
    }

    public TokenResponse exchangeCodeForTokens(String code, String codeVerifier, String clientId, String redirectUri) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", clientId);
        body.add("code", code);
        body.add("redirect_uri", redirectUri);
        body.add("code_verifier", codeVerifier);

        return webClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(body))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .block();
    }
}