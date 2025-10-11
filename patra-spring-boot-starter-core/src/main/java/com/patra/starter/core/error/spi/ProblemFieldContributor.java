package com.patra.starter.core.error.spi;

import java.util.Map;

/**
 * SPI for enriching {@link org.springframework.http.ProblemDetail} responses with custom fields.
 *
 * <p>This starter does not depend on the web stack; implementations should avoid servlet APIs.</p>
 */
public interface ProblemFieldContributor {

    /**
     * Adds custom extension fields to a {@code ProblemDetail} response.
     *
     * @param fields mutable map for extension attributes (never {@code null})
     * @param exception exception being processed (never {@code null})
     */
    void contribute(Map<String, Object> fields, Throwable exception);
}
