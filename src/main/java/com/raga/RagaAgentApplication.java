package com.raga;

import com.raga.config.OpenAIConfig;
import com.raga.config.SafetyConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({OpenAIConfig.class, SafetyConfig.class})
public class RagaAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagaAgentApplication.class, args);
    }
}

