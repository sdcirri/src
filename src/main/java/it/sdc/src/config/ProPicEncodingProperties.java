package it.sdc.src.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "media.propics")
@Getter
@Setter
@Validated
public class ProPicEncodingProperties {
    private @Positive int resolutionPx;
    private @Positive @DecimalMax("100.0") float compressionRatio;
}
