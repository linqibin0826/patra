package com.patra.starter.web.error.spi;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * Web-specific SPI interface for contributing custom fields to ProblemDetail responses.
 * This interface provides access to HttpServletRequest for web-specific field extraction.
 * 
 * @author linqibin
 * @since 0.1.0
 */
public interface WebProblemFieldContributor {
    
    /**
     * Contributes custom fields to the ProblemDetail response with web context.
     * 
     * @param fields the mutable map of fields to add to, must not be null
     * @param exception the exception being handled, must not be null
     * @param request the HTTP servlet request providing web context, must not be null
     */
    void contribute(Map<String, Object> fields, Throwable exception, HttpServletRequest request);
}