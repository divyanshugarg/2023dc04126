package com.raga.service;

import com.raga.config.SafetyConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class SafetyFilterService {

    private final SafetyConfig safetyConfig;

    // Common jailbreak patterns
    private static final List<String> JAILBREAK_PATTERNS = Arrays.asList(
            "ignore previous instructions",
            "forget all previous",
            "you are now",
            "pretend to be",
            "act as if",
            "system prompt",
            "override",
            "bypass",
            "jailbreak",
            "ignore safety",
            "disable safety"
    );

    // Testing domain keywords (positive indicators)
    private static final List<String> TESTING_DOMAIN_KEYWORDS = Arrays.asList(
            "test", "testing", "data", "synthetic", "generate", "mock", "fixture",
            "sample", "dataset", "scenario", "case", "validation", "verify",
            "assert", "expect", "input", "output", "format", "schema", "structure"
    );

    /**
     * Sanitize and validate user input
     */
    public SafetyResult sanitizeAndValidate(String userInput) {
        if (!safetyConfig.getFilter().isEnabled()) {
            return SafetyResult.allowed(userInput);
        }

        String sanitized = sanitizeInput(userInput);
        
        if (safetyConfig.getJailbreak().isDetectionEnabled()) {
            if (detectJailbreakAttempt(sanitized)) {
                log.warn("Jailbreak attempt detected: {}", sanitized);
                return SafetyResult.rejected("Your request contains potentially harmful content. Please rephrase your request to focus on test data generation.");
            }
        }

        if (safetyConfig.getDomainValidation().isEnabled()) {
            if (!isRelevantToTestingDomain(sanitized)) {
                // Allow but log for monitoring
                log.info("Out-of-domain query detected: {}", sanitized);
                // Still allow it but the assistant will handle OOC queries
            }
        }

        return SafetyResult.allowed(sanitized);
    }

    /**
     * Sanitize input by removing potentially dangerous characters and patterns
     */
    private String sanitizeInput(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "";
        }

        // Remove control characters except newlines and tabs
        String sanitized = input.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
        
        // Normalize whitespace
        sanitized = sanitized.replaceAll("\\s+", " ").trim();
        
        // Limit length to prevent abuse
        int maxLength = 5000;
        if (sanitized.length() > maxLength) {
            sanitized = sanitized.substring(0, maxLength);
            log.warn("Input truncated to {} characters", maxLength);
        }

        return sanitized;
    }

    /**
     * Detect potential jailbreak attempts
     */
    private boolean detectJailbreakAttempt(String input) {
        String lowerInput = input.toLowerCase();
        
        for (String pattern : JAILBREAK_PATTERNS) {
            if (lowerInput.contains(pattern)) {
                // Check if it's in a testing context (false positive prevention)
                if (!isInTestingContext(lowerInput, pattern)) {
                    return true;
                }
            }
        }

        // Check for suspicious patterns like role-playing attempts
        Pattern rolePlayPattern = Pattern.compile(
                "(act|pretend|simulate|roleplay|play the role).*(as|of|being).*(admin|root|system|developer)",
                Pattern.CASE_INSENSITIVE
        );
        if (rolePlayPattern.matcher(input).find()) {
            return true;
        }

        return false;
    }

    /**
     * Check if jailbreak pattern is in testing context (false positive prevention)
     */
    private boolean isInTestingContext(String input, String pattern) {
        // If the pattern appears near testing keywords, it's likely legitimate
        int patternIndex = input.indexOf(pattern);
        if (patternIndex == -1) return false;

        String context = input.substring(Math.max(0, patternIndex - 50), 
                                         Math.min(input.length(), patternIndex + pattern.length() + 50));
        
        return TESTING_DOMAIN_KEYWORDS.stream()
                .anyMatch(context::contains);
    }

    /**
     * Check if input is relevant to testing domain
     */
    private boolean isRelevantToTestingDomain(String input) {
        if (input == null || input.trim().isEmpty()) {
            return false;
        }

        String lowerInput = input.toLowerCase();
        
        // Check for testing domain keywords
        boolean hasTestingKeywords = TESTING_DOMAIN_KEYWORDS.stream()
                .anyMatch(lowerInput::contains);

        // Allow small talk and greetings
        boolean isSmallTalk = isSmallTalk(input);

        return hasTestingKeywords || isSmallTalk;
    }

    /**
     * Detect small talk (greetings, pleasantries)
     */
    private boolean isSmallTalk(String input) {
        String lowerInput = input.toLowerCase().trim();
        
        List<String> smallTalkPatterns = Arrays.asList(
                "hello", "hi", "hey", "good morning", "good afternoon", "good evening",
                "thanks", "thank you", "please", "how are you", "what's up",
                "bye", "goodbye", "see you", "help", "can you"
        );

        return smallTalkPatterns.stream()
                .anyMatch(lowerInput::startsWith);
    }

    /**
     * Result of safety filtering
     */
    public static class SafetyResult {
        private final boolean allowed;
        private final String sanitizedInput;
        private final String rejectionReason;

        private SafetyResult(boolean allowed, String sanitizedInput, String rejectionReason) {
            this.allowed = allowed;
            this.sanitizedInput = sanitizedInput;
            this.rejectionReason = rejectionReason;
        }

        public static SafetyResult allowed(String sanitizedInput) {
            return new SafetyResult(true, sanitizedInput, null);
        }

        public static SafetyResult rejected(String reason) {
            return new SafetyResult(false, null, reason);
        }

        public boolean isAllowed() {
            return allowed;
        }

        public String getSanitizedInput() {
            return sanitizedInput;
        }

        public String getRejectionReason() {
            return rejectionReason;
        }
    }
}

