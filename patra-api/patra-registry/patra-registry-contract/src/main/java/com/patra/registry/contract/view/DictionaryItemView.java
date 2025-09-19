package com.patra.registry.contract.view;

/**
 * Dictionary item view object for external subsystem consumption.
 * Used in contract module for clean API boundaries and external system integration.
 * This immutable view object provides a simplified representation of dictionary items
 * optimized for external consumption without internal implementation details.
 * 
 * @param typeCode dictionary type code that this item belongs to
 * @param itemCode dictionary item code, unique within its type
 * @param displayName human-readable display name for UI presentation
 * @param description detailed description of the dictionary item purpose and usage
 * @param isDefault whether this is the default item for its type
 * @param sortOrder numeric sort order for display ordering (lower values appear first)
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryItemView(
    String typeCode,
    String itemCode,
    String displayName,
    String description,
    boolean isDefault,
    int sortOrder
) {
    
    /**
     * Creates a new DictionaryItemView with validation.
     * 
     * @param typeCode dictionary type code that this item belongs to
     * @param itemCode dictionary item code, unique within its type
     * @param displayName human-readable display name for UI presentation
     * @param description detailed description of the dictionary item
     * @param isDefault whether this is the default item for its type
     * @param sortOrder numeric sort order for display ordering
     * @throws IllegalArgumentException if typeCode, itemCode, or displayName is null or empty
     */
    public DictionaryItemView {
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
     * Gets a formatted reference string for this dictionary item.
     * Useful for logging, debugging, and external system integration.
     * 
     * @return a string in the format "typeCode:itemCode"
     */
    public String getReferenceString() {
        return typeCode + ":" + itemCode;
    }
    
    /**
     * Gets a display-friendly label combining the item code and display name.
     * Useful for dropdown lists and selection components in external systems.
     * 
     * @return a string in the format "itemCode - displayName"
     */
    public String getDisplayLabel() {
        return itemCode + " - " + displayName;
    }
    
    /**
     * Checks if this item has a meaningful description.
     * 
     * @return true if description is not null and not empty, false otherwise
     */
    public boolean hasDescription() {
        return description != null && !description.trim().isEmpty();
    }
    
    /**
     * Creates a simplified view with only essential fields for basic display.
     * Useful when only basic information is needed for external system integration.
     * 
     * @return a new DictionaryItemView with empty description and default sort order
     */
    public DictionaryItemView toSimplifiedView() {
        return new DictionaryItemView(typeCode, itemCode, displayName, "", isDefault, 0);
    }
}