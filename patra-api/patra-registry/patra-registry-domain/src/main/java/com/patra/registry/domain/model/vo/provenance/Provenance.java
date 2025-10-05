package com.patra.registry.domain.model.vo.provenance;

import com.patra.registry.domain.exception.DomainValidationException;

/**
 * Domain value object for {@code reg_provenance}.
 *
 * <p>Represents the root provenance entity referenced by all reg_prov_* configs.</p>
 *
 * @author linqibin
 * @since 0.1.0
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

        this.id = id; // already validated as positive
        this.code = codeTrimmed;
        this.name = nameTrimmed;
        this.baseUrlDefault = baseUrlDefault != null ? baseUrlDefault.trim() : null;
        this.timezoneDefault = tzTrimmed;
        this.docsUrl = docsUrl != null ? docsUrl.trim() : null;
        this.active = active;
        this.lifecycleStatusCode = lifecycleTrimmed;
    }

    /** Indicates whether the provenance is active. */
    public boolean isActive() {
        return active;
    }
}
