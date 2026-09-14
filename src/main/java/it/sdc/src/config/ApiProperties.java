package it.sdc.src.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "api")
@Getter
@Setter
@Validated
public class ApiProperties {
    private String base = "";
}
