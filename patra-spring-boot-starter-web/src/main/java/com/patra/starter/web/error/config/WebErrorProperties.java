package com.patra.starter.web.error.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.ProblemDetail;

/**
 * Configuration properties that govern Web-layer error responses (e.g. {@link ProblemDetail}).
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@ConfigurationProperties(prefix = "patra.web.problem")
public class WebErrorProperties {
    
    /** Whether Web-specific error handling is enabled. */
    private boolean enabled = true;
    
    /** Base URL used to construct the {@code ProblemDetail#type} attribute. */
    private String typeBaseUrl = "https://errors.example.com/";
    
    /** Whether stack traces should be included in the response (debug purposes only). */
    private boolean includeStack = false;
}
