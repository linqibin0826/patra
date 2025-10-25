package com.patra.registry.api.dto.dict;

/**
 * Dictionary item metadata exposed to subsystem clients.
 *
 * <p>Field descriptions:
 *
 * <ol>
 *   <li>typeCode - dictionary type identifier
 *   <li>itemCode - item code within the dictionary type
 *   <li>displayName - localized display name for the item
 *   <li>description - optional human-readable description
 *   <li>isDefault - whether the item is marked as default
 *   <li>sortOrder - ordering hint within the dictionary
 *   <li>enabled - whether the item is currently active
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
    boolean enabled) {}
