package com.patra.registry.domain.model.read.provenance;

import com.patra.registry.domain.exception.DomainValidationException;

/**
 * Provenance query view.
 *
 * <p>Read-optimized projection for querying provenance metadata.
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ProvenanceQuery(
        Long id,
        String code,
        String name,
        String baseUrlDefault,
        String timezoneDefault,
        String docsUrl,
        boolean active,
        String lifecycleStatusCode
) {
    public ProvenanceQuery {
        if (id == null || id <= 0) {
            throw new DomainValidationException("Provenance id must be positive");
        }
        if (code == null || code.isBlank()) {
            throw new DomainValidationException("Provenance code cannot be blank");
        }
        if (name == null || name.isBlank()) {
            throw new DomainValidationException("Provenance name cannot be blank");
        }
        if (timezoneDefault == null || timezoneDefault.isBlank()) {
            throw new DomainValidationException("Timezone default cannot be blank");
        }
        if (lifecycleStatusCode == null || lifecycleStatusCode.isBlank()) {
            throw new DomainValidationException("Lifecycle status code cannot be blank");
        }
        code = code.trim();
        name = name.trim();
        baseUrlDefault = baseUrlDefault != null ? baseUrlDefault.trim() : null;
        timezoneDefault = timezoneDefault.trim();
        docsUrl = docsUrl != null ? docsUrl.trim() : null;
        lifecycleStatusCode = lifecycleStatusCode.trim();
    }
}
