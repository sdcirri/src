package it.sdc.src.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

@Configuration
public class CryptoConfig {
    @Bean
    public SecureRandom secureRandom() {
        return new SecureRandom();
    }

    @Bean
    public MessageDigest sha512() {
        try {
            return MessageDigest.getInstance("SHA-512");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("SHA-512 hash algorithm is not available");
        }
    }
}
