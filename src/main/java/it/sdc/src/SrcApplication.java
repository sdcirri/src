package it.sdc.src;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(
        exclude = UserDetailsServiceAutoConfiguration.class
)
@ConfigurationPropertiesScan
public class SrcApplication {
    static void main(String[] args) {
        SpringApplication.run(SrcApplication.class, args);
    }
}
