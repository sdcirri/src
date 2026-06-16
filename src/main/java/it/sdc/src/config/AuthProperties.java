package it.sdc.src.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth")
@Getter
@Setter
public class AuthProperties {
    private Long accessTokenValiditySeconds;
    private Long refreshTokenValiditySeconds;
}
