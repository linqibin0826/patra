package com.patra.common.error.codes;

/**
 * Contract for business error codes that can be used across the error handling system.
 * Implementations should provide a unique code identifier that can be used for
 * error resolution, mapping, and client-side error handling.
 * 
 * @author linqibin
 * @since 0.1.0
 */
public interface ErrorCodeLike {
    
    /**
     * Returns the unique error code identifier.
     * The code should follow a consistent format (e.g., "REG-0404", "ORD-1001")
     * and be suitable for both human reading and programmatic handling.
     * 
     * @return the error code string, must not be null or empty
     */
    String code();
}