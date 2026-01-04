package com.raga.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Configuration
@Slf4j
public class OpenAIKeyConfig {

    @Value("${openai.api.key:}")
    private String apiKeyFromProperties;

    @Bean
    public String openAIApiKey() {
        // First try environment variable
        String apiKey = System.getenv("OPENAI_API_KEY");
        
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            log.info("Using OpenAI API key from environment variable");
            return apiKey.trim();
        }

        // Then try from properties
        if (apiKeyFromProperties != null && !apiKeyFromProperties.trim().isEmpty()) {
            log.info("Using OpenAI API key from application.properties");
            return apiKeyFromProperties.trim();
        }

        // Finally, try reading from open_ai_key file in project root
        try {
            String projectRoot = System.getProperty("user.dir");
            String keyFilePath = Paths.get(projectRoot, "open_ai_key").toString();
            
            if (Files.exists(Paths.get(keyFilePath))) {
                String keyFromFile = Files.readString(Paths.get(keyFilePath)).trim();
                if (!keyFromFile.isEmpty()) {
                    log.info("Using OpenAI API key from file: open_ai_key");
                    return keyFromFile;
                }
            }
        } catch (IOException e) {
            log.warn("Could not read API key from file", e);
        }

        log.error("No OpenAI API key found! Please set OPENAI_API_KEY environment variable, " +
                 "configure openai.api.key in application.properties, or create open_ai_key file.");
        return "";
    }
}

