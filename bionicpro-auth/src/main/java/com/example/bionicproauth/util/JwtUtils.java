package com.example.bionicproauth.util;


import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;

@Component
public class JwtUtils {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String extractEmail(String accessToken) {
        try {
            String[] chunks = accessToken.split("\\.");
            if (chunks.length < 2) return null;
            String payload = new String(Base64.getUrlDecoder().decode(chunks[1]));
            JsonNode node = objectMapper.readTree(payload);
            return node.get("email").asText();
        } catch (Exception e) {
            return null;
        }
    }
}