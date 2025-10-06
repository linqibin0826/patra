package com.patra.registry.api.rpc.dto.provenance;

/**
 * Response DTO exposing core provenance metadata to downstream services.
 *
 * <p>Field descriptions:
 * <ol>
 *   <li>id - internal identifier of the provenance row</li>
 *   <li>code - stable business code uniquely identifying the provenance</li>
 *   <li>name - human-readable provenance name</li>
 *   <li>baseUrlDefault - default base URL used when no endpoint override exists</li>
 *   <li>timezoneDefault - default timezone applied when interpreting schedules</li>
 *   <li>docsUrl - documentation or reference URL for the provenance</li>
 *   <li>active - whether the provenance is currently active</li>
 *   <li>lifecycleStatusCode - lifecycle status discriminator (PLANNING/ACTIVE/etc.)</li>
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
        String lifecycleStatusCode
) {
}
