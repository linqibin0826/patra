package com.patra.starter.core.error.spi;

import java.util.Map;

/**
 * SPI interface for contributing custom fields to ProblemDetail responses.
 * Core version without web dependencies - implementations should not depend on HttpServletRequest.
 * 
 * @author linqibin
 * @since 0.1.0
 */
public interface ProblemFieldContributor {
    
    /**
     * Contributes custom fields to the ProblemDetail response.
     * 
     * @param fields the mutable map of fields to add to, must not be null
     * @param exception the exception being handled, must not be null
     */
    void contribute(Map<String, Object> fields, Throwable exception);
}