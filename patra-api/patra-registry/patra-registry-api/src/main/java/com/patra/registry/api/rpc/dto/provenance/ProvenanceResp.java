package com.patra.registry.api.rpc.dto.provenance;

/**
 * Provenance 基础响应 DTO。
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
