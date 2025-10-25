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
    String lifecycleStatusCode) {
  public ProvenanceQuery {
    DomainValidationException.positive(id, "Provenance id");
    code = DomainValidationException.notBlank(code, "Provenance code");
    name = DomainValidationException.notBlank(name, "Provenance name");
    timezoneDefault = DomainValidationException.notBlank(timezoneDefault, "Timezone default");
    lifecycleStatusCode =
        DomainValidationException.notBlank(lifecycleStatusCode, "Lifecycle status code");
    baseUrlDefault = DomainValidationException.trimOrNull(baseUrlDefault);
    docsUrl = DomainValidationException.trimOrNull(docsUrl);
  }
}
