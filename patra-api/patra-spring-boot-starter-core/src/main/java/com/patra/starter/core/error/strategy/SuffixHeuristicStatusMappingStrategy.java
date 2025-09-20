package com.patra.starter.core.error.strategy;

import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.starter.core.error.spi.StatusMappingStrategy;
import lombok.extern.slf4j.Slf4j;

/**
 * Status mapping strategy that uses suffix heuristics to map error codes to HTTP status codes.
 * Extracts the suffix from error codes (e.g., REG-0404 → 404) and uses it as HTTP status.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public class SuffixHeuristicStatusMappingStrategy implements StatusMappingStrategy {
    
    @Override
    public int mapToHttpStatus(ErrorCodeLike errorCode, Throwable exception) {
        if (errorCode == null) {
            log.debug("Error code is null, defaulting to 500");
            return 500;
        }
        
        String code = errorCode.code();
        if (code == null || code.isEmpty()) {
            log.debug("Error code string is null or empty, defaulting to 500");
            return 500;
        }
        
        // Extract suffix after last dash (e.g., REG-0404 → 0404)
        int lastDashIndex = code.lastIndexOf('-');
        if (lastDashIndex == -1 || lastDashIndex == code.length() - 1) {
            log.debug("Error code '{}' has no suffix after dash, defaulting to 500", code);
            return 500;
        }
        
        String suffix = code.substring(lastDashIndex + 1);
        
        try {
            int status = Integer.parseInt(suffix);
            
            // Validate HTTP status code range
            if (status < 100 || status > 599) {
                log.debug("Parsed status {} from code '{}' is outside valid HTTP range, defaulting to 500", 
                         status, code);
                return 500;
            }
            
            log.debug("Mapped error code '{}' to HTTP status {}", code, status);
            return status;
            
        } catch (NumberFormatException e) {
            log.debug("Could not parse suffix '{}' from error code '{}' as integer, defaulting to 500", 
                     suffix, code);
            return 500;
        }
    }
}