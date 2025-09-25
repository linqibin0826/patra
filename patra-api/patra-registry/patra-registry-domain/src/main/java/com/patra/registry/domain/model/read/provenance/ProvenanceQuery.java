package com.patra.registry.domain.model.read.provenance;

/**
 * Provenance 基础信息查询视图。
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
            throw new IllegalArgumentException("Provenance id must be positive");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Provenance code cannot be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Provenance name cannot be blank");
        }
        if (timezoneDefault == null || timezoneDefault.isBlank()) {
            throw new IllegalArgumentException("Timezone default cannot be blank");
        }
        if (lifecycleStatusCode == null || lifecycleStatusCode.isBlank()) {
            throw new IllegalArgumentException("Lifecycle status code cannot be blank");
        }
        code = code.trim();
        name = name.trim();
        baseUrlDefault = baseUrlDefault != null ? baseUrlDefault.trim() : null;
        timezoneDefault = timezoneDefault.trim();
        docsUrl = docsUrl != null ? docsUrl.trim() : null;
        lifecycleStatusCode = lifecycleStatusCode.trim();
    }
}
