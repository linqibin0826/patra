package com.patra.registry.domain.model.vo.provenance;

import com.patra.registry.domain.exception.DomainValidationException;

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
        DomainValidationException.positive(id, "Provenance id");
        String codeTrimmed = DomainValidationException.notBlank(code, "Provenance code");
        String nameTrimmed = DomainValidationException.notBlank(name, "Provenance name");
        String tzTrimmed = DomainValidationException.notBlank(timezoneDefault, "Timezone");
        String lifecycleTrimmed = DomainValidationException.notBlank(lifecycleStatusCode, "Lifecycle status code");

        this.id = id; // 已验证为正
        this.code = codeTrimmed;
        this.name = nameTrimmed;
        this.baseUrlDefault = baseUrlDefault != null ? baseUrlDefault.trim() : null;
        this.timezoneDefault = tzTrimmed;
        this.docsUrl = docsUrl != null ? docsUrl.trim() : null;
        this.active = active;
        this.lifecycleStatusCode = lifecycleTrimmed;
    }

    /** 是否处于激活状态。 */
    public boolean isActive() {
        return active;
    }
}
