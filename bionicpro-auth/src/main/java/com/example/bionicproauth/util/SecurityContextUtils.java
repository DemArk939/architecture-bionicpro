package com.example.bionicproauth.util;

import com.example.bionicproauth.model.TokenResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;
import java.util.List;

public class SecurityContextUtils {

    public static Authentication createAuthentication(String username, TokenResponse tokens) {
        // В реальности можно извлечь роли из access token (JWT)
        List<SimpleGrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
        return new UsernamePasswordAuthenticationToken(username, tokens, authorities);
    }
}