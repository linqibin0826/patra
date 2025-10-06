package com.patra.registry.api.rpc.dto.dict;

/**
 * Response DTO exposed to subsystem clients for dictionary item lookups.
 *
 * <p>Field descriptions:
 * <ol>
 *   <li>typeCode - dictionary type identifier</li>
 *   <li>itemCode - item code within the dictionary type</li>
 *   <li>displayName - localized display name for the item</li>
 *   <li>description - optional human-readable description</li>
 *   <li>isDefault - whether the item is marked as default</li>
 *   <li>sortOrder - ordering hint within the dictionary</li>
 *   <li>enabled - whether the item is currently active</li>
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
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
