package it.sdc.src.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.cors")
@Getter
@Setter
public class AppCorsProperties {
    private List<String> allowedOriginPatterns = List.of();
}
