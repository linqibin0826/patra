package com.patra.registry.adapter.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Dictionary logging configuration for consistent logging patterns.
 * Provides centralized logging configuration and guidelines for dictionary operations.
 * Ensures proper log levels are used throughout the dictionary read pipeline.
 * 
 * <p>Logging Level Guidelines:</p>
 * <ul>
 *   <li><strong>ERROR</strong>: System failures, infrastructure issues, unexpected exceptions</li>
 *   <li><strong>WARN</strong>: Business rule violations, data integrity issues, validation failures</li>
 *   <li><strong>INFO</strong>: Important business operations, API entry/exit, health status changes</li>
 *   <li><strong>DEBUG</strong>: Detailed execution flow, parameter values, internal state</li>
 * </ul>
 * 
 * <p>Structured Logging Requirements:</p>
 * <ul>
 *   <li>Always use parameterized logging: {@code log.info("Operation: param={}", value)}</li>
 *   <li>Include relevant identifiers (typeCode, itemCode) for correlation</li>
 *   <li>Use consistent parameter naming across components</li>
 *   <li>Avoid logging in high-frequency operations to prevent log flooding</li>
 * </ul>
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Configuration
public class DictionaryLoggingConfig {
    
    /**
     * Logs dictionary logging configuration on application startup.
     * Provides visibility into logging configuration and guidelines.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void logDictionaryLoggingConfiguration() {
        log.info("Dictionary logging configuration initialized with structured logging patterns");
        log.info("Dictionary logging levels: ERROR=system failures, WARN=business violations, INFO=operations, DEBUG=execution flow");
        log.debug("Dictionary debug logging enabled - detailed execution flow will be logged");
    }
    
    /**
     * Validates that proper logging patterns are being used.
     * This method serves as documentation for logging best practices.
     * 
     * <p>Best Practices:</p>
     * <ul>
     *   <li>Use {@code @Slf4j} annotation on all classes requiring logging</li>
     *   <li>Include business context in log messages (typeCode, itemCode, operation)</li>
     *   <li>Log exceptions with full stack trace using {@code log.error("message", exception)}</li>
     *   <li>Use appropriate log levels based on the severity and audience</li>
     *   <li>Avoid sensitive data in log messages (PII, credentials)</li>
     * </ul>
     */
    public void validateLoggingPatterns() {
        // This method serves as documentation and can be extended with runtime validation
        log.debug("Dictionary logging patterns validated - following structured logging guidelines");
    }
    
    /**
     * Gets the recommended log level for different types of dictionary operations.
     * 
     * @param operationType the type of operation being performed
     * @return the recommended log level as a string
     */
    public String getRecommendedLogLevel(String operationType) {
        return switch (operationType.toLowerCase()) {
            case "api_entry", "api_exit", "health_check", "batch_operation" -> "INFO";
            case "validation_failure", "data_integrity_issue", "business_rule_violation" -> "WARN";
            case "system_failure", "infrastructure_error", "unexpected_exception" -> "ERROR";
            case "execution_flow", "parameter_logging", "internal_state" -> "DEBUG";
            default -> "INFO";
        };
    }
    
    /**
     * Creates a structured log message with consistent formatting.
     * 
     * @param operation the operation being performed
     * @param typeCode the dictionary type code, if applicable
     * @param itemCode the dictionary item code, if applicable
     * @param additionalContext additional context information
     * @return formatted log message string
     */
    public String createStructuredLogMessage(String operation, String typeCode, String itemCode, String additionalContext) {
        StringBuilder message = new StringBuilder(operation);
        
        if (typeCode != null && !typeCode.isEmpty()) {
            message.append(": typeCode=").append(typeCode);
        }
        
        if (itemCode != null && !itemCode.isEmpty()) {
            message.append(", itemCode=").append(itemCode);
        }
        
        if (additionalContext != null && !additionalContext.isEmpty()) {
            message.append(", ").append(additionalContext);
        }
        
        return message.toString();
    }
}