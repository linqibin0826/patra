package com.patra.registry.api.rpc.dto.dict;

/**
 * Response DTO exposed to subsystem clients for dictionary item lookups.
 */
public record DictionaryItemResp(
        String typeCode,
        String itemCode,
        String displayName,
        String description,
        boolean isDefault,
        int sortOrder,
        boolean enabled
) {
}
