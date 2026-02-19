package com.example.bionicproauth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;

@Service
public class TokenEncryptionService {

    private final TextEncryptor encryptor;

    public TokenEncryptionService(@Value("${token.encryption.secret}") String secret) {
        // Используем фиксированную соль для простоты (в реальном проекте лучше генерировать случайную)
        this.encryptor = Encryptors.text(secret, "deadbeefdeadbeef");
    }

    public String encrypt(String data) {
        return encryptor.encrypt(data);
    }

    public String decrypt(String encryptedData) {
        return encryptor.decrypt(encryptedData);
    }
}