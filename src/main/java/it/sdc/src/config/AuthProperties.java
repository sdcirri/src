package it.sdc.src.config;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "auth")
@Getter
@Setter
@Validated
public class AuthProperties {
    private @Positive long accessTokenValiditySeconds;
    private @Positive long refreshTokenValiditySeconds;
}
