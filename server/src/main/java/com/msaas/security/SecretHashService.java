package com.msaas.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class SecretHashService {

    public String hash(String rawSecret) {
        if (rawSecret == null || rawSecret.isBlank()) {
            throw new IllegalArgumentException("Secret must not be blank");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawSecret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    public String preview(String rawSecret) {
        if (rawSecret == null || rawSecret.length() < 16) {
            return "hidden";
        }
        return rawSecret.substring(0, 8) + "..." + rawSecret.substring(rawSecret.length() - 4);
    }

    public boolean matches(String rawSecret, String hash) {
        if (rawSecret == null || rawSecret.isBlank() || hash == null || hash.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(hash(rawSecret).getBytes(StandardCharsets.UTF_8), hash.getBytes(StandardCharsets.UTF_8));
    }
}
