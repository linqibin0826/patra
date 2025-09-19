package com.patra.registry.api.rpc.dto.dict;

/**
 * Request payload representing a dictionary reference that needs validation.
 * <p>
 * The request mirrors the structure of the domain {@code DictionaryReference}
 * value object but stays independent from the contract module to keep the
 * HTTP layer decoupled from internal representations.
 */
public record DictionaryReferenceReq(
        String typeCode,
        String itemCode
) {
    public DictionaryReferenceReq {
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary type code cannot be null or empty");
        }
        if (itemCode == null || itemCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary item code cannot be null or empty");
        }
        typeCode = typeCode.trim();
        itemCode = itemCode.trim();
    }
}
