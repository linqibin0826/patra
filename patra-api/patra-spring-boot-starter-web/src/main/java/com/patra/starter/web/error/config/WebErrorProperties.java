package com.patra.starter.web.error.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Web-specific error handling configuration properties.
 * Provides configuration for web error handling behavior including ProblemDetail formatting.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Data
@ConfigurationProperties(prefix = "patra.web.problem")
public class WebErrorProperties {
    
    /** Whether web error handling is enabled */
    private boolean enabled = true;
    
    /** Base URL for ProblemDetail type field construction */
    private String typeBaseUrl = "https://errors.example.com/";
    
    /** Whether to include stack traces in error responses (for debugging) */
    private boolean includeStack = false;
}