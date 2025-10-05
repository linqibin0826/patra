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
        /* Primary key; unique source identifier referenced by all downstream configurations */
        Long id,
        /* Source code: globally unique, stable (e.g., pubmed/crossref); used for lookups and constraints */
        String code,
        /* Source display name (e.g., PubMed / Crossref) for human readability */
        String name,
        /* Default base URL for this source; joined with endpoint paths to form complete API URLs */
        String baseUrlDefault,
        /* Default timezone (IANA TZ, e.g., UTC/Asia/Shanghai): default for window calc/display */
        String timezoneDefault,
        /* Official docs/reference URL: helps troubleshooting and API verification */
        String docsUrl,
        /* Whether this source is active: {@code true}=active, {@code false}=inactive (read side may filter by this) */
        boolean active,
        /* Lifecycle status code (DICT CODE: lifecycle_status); read side uses ACTIVE/valid only */
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

    /**
     * Indicates whether the provenance is active.
     *
     * @return {@code true} if the source is active, {@code false} otherwise
     */
    public boolean isActive() {
        return active;
    }
}
