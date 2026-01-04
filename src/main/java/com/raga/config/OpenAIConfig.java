package com.raga.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "openai")
public class OpenAIConfig {
    private Api api = new Api();
    private Assistant assistant = new Assistant();

    @Data
    public static class Api {
        private String key;
        private String baseUrl;
    }

    @Data
    public static class Assistant {
        private String id;
    }
}

