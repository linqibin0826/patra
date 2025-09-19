package com.patra.registry.contract.query.view;

/**
 * Dictionary item query object for CQRS read operations.
 * Shared between app module and contract module for consistent data structure.
 * This immutable query object represents dictionary item data optimized for read operations
 * and external subsystem consumption via API contracts.
 * 
 * @param typeCode dictionary type code that this item belongs to
 * @param itemCode dictionary item code, unique within its type
 * @param displayName human-readable display name for UI presentation
 * @param description detailed description of the dictionary item purpose and usage
 * @param isDefault whether this is the default item for its type (only one per type should be true)
 * @param sortOrder numeric sort order for display ordering (lower values appear first)
 * @param enabled whether the item is currently enabled and available for use
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryItemQuery(
    String typeCode,
    String itemCode,
    String displayName,
    String description,
    boolean isDefault,
    int sortOrder,
    boolean enabled
) {
    
    /**
     * Creates a new DictionaryItemQuery with validation.
     * 
     * @param typeCode dictionary type code that this item belongs to
     * @param itemCode dictionary item code, unique within its type
     * @param displayName human-readable display name for UI presentation
     * @param description detailed description of the dictionary item
     * @param isDefault whether this is the default item for its type
     * @param sortOrder numeric sort order for display ordering
     * @param enabled whether the item is currently enabled and available
     * @throws IllegalArgumentException if typeCode, itemCode, or displayName is null or empty
     */
    public DictionaryItemQuery {
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary type code cannot be null or empty");
        }
        if (itemCode == null || itemCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary item code cannot be null or empty");
        }
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary item display name cannot be null or empty");
        }
        
        // Normalize the codes and names to ensure consistency
        typeCode = typeCode.trim();
        itemCode = itemCode.trim();
        displayName = displayName.trim();
        description = description != null ? description.trim() : "";
    }
    
    /**
     * Checks if this dictionary item is available for use.
     * An item is available if it is enabled.
     * 
     * @return true if the item is available for use, false otherwise
     */
    public boolean isAvailable() {
        return enabled;
    }
    
    /**
     * Gets a formatted reference string for this dictionary item.
     * 
     * @return a string in the format "typeCode:itemCode"
     */
    public String getReferenceString() {
        return typeCode + ":" + itemCode;
    }
    
    /**
     * Checks if this item can serve as a default value.
     * An item can be default only if it is available and marked as default.
     * 
     * @return true if this item can serve as a default value, false otherwise
     */
    public boolean canBeDefault() {
        return isAvailable() && isDefault;
    }
}