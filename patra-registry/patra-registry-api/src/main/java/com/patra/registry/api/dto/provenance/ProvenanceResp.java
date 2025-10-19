package com.patra.registry.api.dto.provenance;

/**
 * Response DTO exposing core provenance metadata to downstream services.
 *
 * <p>Field descriptions:
 *
 * <ol>
 *   <li>id - internal identifier of the provenance row
 *   <li>code - stable business code uniquely identifying the provenance
 *   <li>name - human-readable provenance name
 *   <li>baseUrlDefault - default base URL used when no endpoint override exists
 *   <li>timezoneDefault - default timezone applied when interpreting schedules
 *   <li>docsUrl - documentation or reference URL for the provenance
 *   <li>active - whether the provenance is currently active
 *   <li>lifecycleStatusCode - lifecycle status discriminator (PLANNING/ACTIVE/etc.)
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ProvenanceResp(
    Long id,
    String code,
    String name,
    String baseUrlDefault,
    String timezoneDefault,
    String docsUrl,
    boolean active,
    String lifecycleStatusCode) {}
