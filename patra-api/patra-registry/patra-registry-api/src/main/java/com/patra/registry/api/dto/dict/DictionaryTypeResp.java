package com.patra.registry.api.dto.dict;

/**
 * Response DTO for dictionary type metadata exposed via the internal HTTP API.
 *
 * <p>Field descriptions:
 *
 * <ol>
 *   <li>typeCode - dictionary type identifier
 *   <li>typeName - human-readable type name
 *   <li>description - optional description for the type
 *   <li>enabledItemCount - number of enabled items under the type
 *   <li>hasDefault - whether the type currently has a default item
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
    boolean hasDefault) {}
