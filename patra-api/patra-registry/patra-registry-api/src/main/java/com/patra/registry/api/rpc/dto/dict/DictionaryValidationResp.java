package com.patra.registry.api.rpc.dto.dict;

/**
 * Response DTO representing the validation result for a dictionary reference.
 */
public record DictionaryValidationResp(
        String typeCode,
        String itemCode,
        boolean valid,
        String errorMessage
) {
}
