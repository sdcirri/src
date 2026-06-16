package it.sdc.src;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SrcApplication {
    static void main(String[] args) {
        SpringApplication.run(SrcApplication.class, args);
    }
}
