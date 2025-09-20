package com.patra.registry.domain.model.vo.provenance;

/**
 * {@code reg_provenance} 的领域值对象。
 */
public record Provenance(
        Long id,
        String code,
        String name,
        String baseUrlDefault,
        String timezoneDefault,
        String docsUrl,
        boolean active,
        String lifecycleStatusCode
) {
    public Provenance(Long id,
                      String code,
                      String name,
                      String baseUrlDefault,
                      String timezoneDefault,
                      String docsUrl,
                      boolean active,
                      String lifecycleStatusCode) {
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
            throw new IllegalArgumentException("Timezone cannot be blank");
        }
        if (lifecycleStatusCode == null || lifecycleStatusCode.isBlank()) {
            throw new IllegalArgumentException("Lifecycle status code cannot be blank");
        }

        this.id = id;
        this.code = code.trim();
        this.name = name.trim();
        this.baseUrlDefault = baseUrlDefault != null ? baseUrlDefault.trim() : null;
        this.timezoneDefault = timezoneDefault.trim();
        this.docsUrl = docsUrl != null ? docsUrl.trim() : null;
        this.active = active;
        this.lifecycleStatusCode = lifecycleStatusCode.trim();
    }

    /** 是否处于激活状态。 */
    public boolean isActive() {
        return active;
    }
}
