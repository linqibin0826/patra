package com.patra.registry.api.rpc.dto.dict;

/**
 * Response DTO for dictionary type metadata exposed via the internal HTTP API.
 */
public record DictionaryTypeResp(
        String typeCode,
        String typeName,
        String description,
        int enabledItemCount,
        boolean hasDefault
) {
}
