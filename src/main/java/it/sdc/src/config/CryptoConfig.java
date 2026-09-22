package it.sdc.src.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;

@Configuration
public class CryptoConfig {
    @Bean
    public SecureRandom secureRandom() {
        return new SecureRandom();
    }
}
