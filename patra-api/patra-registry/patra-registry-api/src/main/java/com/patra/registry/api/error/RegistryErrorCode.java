package com.patra.registry.api.error;

import com.patra.common.error.codes.ErrorCodeLike;

/**
 * Registry service error code catalog implementing the ErrorCodeLike contract.
 * This enum provides a comprehensive catalog of all Registry service error codes
 * following the REG-NNNN format with Registry context prefix.
 * 
 * Error codes are organized into categories:
 * - 0xxx: Common HTTP-aligned codes
 * - 1xxx: Business-specific codes
 * 
 * This catalog follows an append-only principle - new codes can be added but
 * existing codes should never be removed or modified to maintain API stability.
 * 
 * @author linqibin
 * @since 0.1.0
 */
public enum RegistryErrorCode implements ErrorCodeLike {
    
    // ========================================
    // Common HTTP-aligned codes (0xxx series)
    // ========================================
    
    /**
     * Bad Request - The request was invalid or malformed.
     * Corresponds to HTTP 400 Bad Request.
     */
    REG_0400("REG-0400"),
    
    /**
     * Unauthorized - Authentication is required or has failed.
     * Corresponds to HTTP 401 Unauthorized.
     */
    REG_0401("REG-0401"),
    
    /**
     * Forbidden - The request is understood but access is denied.
     * Corresponds to HTTP 403 Forbidden.
     */
    REG_0403("REG-0403"),
    
    /**
     * Not Found - The requested resource could not be found.
     * Corresponds to HTTP 404 Not Found.
     */
    REG_0404("REG-0404"),
    
    /**
     * Conflict - The request conflicts with the current state of the resource.
     * Corresponds to HTTP 409 Conflict.
     */
    REG_0409("REG-0409"),
    
    /**
     * Unprocessable Entity - The request is well-formed but contains semantic errors.
     * Corresponds to HTTP 422 Unprocessable Entity.
     */
    REG_0422("REG-0422"),
    
    /**
     * Too Many Requests - Rate limit exceeded or quota exhausted.
     * Corresponds to HTTP 429 Too Many Requests.
     */
    REG_0429("REG-0429"),
    
    /**
     * Internal Server Error - An unexpected error occurred on the server.
     * Corresponds to HTTP 500 Internal Server Error.
     */
    REG_0500("REG-0500"),
    
    /**
     * Service Unavailable - The service is temporarily unavailable.
     * Corresponds to HTTP 503 Service Unavailable.
     */
    REG_0503("REG-0503"),
    
    /**
     * Gateway Timeout - Timeout occurred while waiting for upstream service.
     * Corresponds to HTTP 504 Gateway Timeout.
     */
    REG_0504("REG-0504"),
    
    // ========================================
    // Business-specific codes (1xxx series)
    // ========================================
    
    // Dictionary operations (14xx series)
    
    /**
     * Dictionary Type Not Found - The specified dictionary type could not be found.
     * Maps to: DictionaryNotFoundException (for type-level operations)
     */
    REG_1401("REG-1401"),
    
    /**
     * Dictionary Item Not Found - The specified dictionary item could not be found.
     * Maps to: DictionaryNotFoundException (for item-level operations)
     */
    REG_1402("REG-1402"),
    
    /**
     * Dictionary Item Disabled - The specified dictionary item is disabled.
     * Maps to: DictionaryItemDisabled
     */
    REG_1403("REG-1403"),
    
    /**
     * Dictionary Type Already Exists - Attempted to create a dictionary type that already exists.
     * Maps to: DictionaryTypeAlreadyExists
     */
    REG_1404("REG-1404"),
    
    /**
     * Dictionary Item Already Exists - Attempted to create a dictionary item that already exists.
     * Maps to: DictionaryItemAlreadyExists
     */
    REG_1405("REG-1405"),
    
    /**
     * Dictionary Type Disabled - The specified dictionary type is disabled.
     * Maps to: DictionaryTypeDisabled
     */
    REG_1406("REG-1406"),
    
    /**
     * Dictionary Validation Error - The dictionary data failed validation.
     * Maps to: DictionaryValidationException
     */
    REG_1407("REG-1407"),
    
    /**
     * Dictionary Default Item Missing - Required default item is missing from dictionary type.
     * Maps to: DictionaryDefaultItemMissing
     */
    REG_1408("REG-1408"),
    
    /**
     * Dictionary Repository Error - Database or repository layer error occurred.
     * Maps to: DictionaryRepositoryException
     */
    REG_1409("REG-1409"),
    
    // Registry general operations (15xx series)
    
    /**
     * Registry Quota Exceeded - Operation would exceed system quotas or limits.
     * Maps to: RegistryQuotaExceeded
     */
    REG_1501("REG-1501");
    
    private final String code;
    
    /**
     * Constructs a RegistryErrorCode with the specified code string.
     * 
     * @param code the error code string following REG-NNNN format
     */
    RegistryErrorCode(String code) {
        this.code = code;
    }
    
    /**
     * Returns the unique error code identifier.
     * 
     * @return the error code string in REG-NNNN format
     */
    @Override
    public String code() {
        return code;
    }
    
    /**
     * Returns the error code string representation.
     * 
     * @return the error code string
     */
    @Override
    public String toString() {
        return code;
    }
}