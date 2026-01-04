package com.raga.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "safety")
public class SafetyConfig {
    private Filter filter = new Filter();
    private Jailbreak jailbreak = new Jailbreak();
    private DomainValidation domainValidation = new DomainValidation();

    @Data
    public static class Filter {
        private boolean enabled;
    }

    @Data
    public static class Jailbreak {
        private boolean detectionEnabled;
    }

    @Data
    public static class DomainValidation {
        private boolean enabled;
    }
}

