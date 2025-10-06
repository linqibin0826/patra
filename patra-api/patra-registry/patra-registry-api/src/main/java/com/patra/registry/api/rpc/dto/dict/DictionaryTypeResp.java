package com.patra.registry.api.rpc.dto.dict;

/**
 * Response DTO for dictionary type metadata exposed via the internal HTTP API.
 *
 * <p>Field descriptions:
 * <ol>
 *   <li>typeCode - dictionary type identifier</li>
 *   <li>typeName - human-readable type name</li>
 *   <li>description - optional description for the type</li>
 *   <li>enabledItemCount - number of enabled items under the type</li>
 *   <li>hasDefault - whether the type currently has a default item</li>
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryTypeResp(
        String typeCode,
        String typeName,
        String description,
        int enabledItemCount,
        boolean hasDefault
) {
}
