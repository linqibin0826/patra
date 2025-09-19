package com.patra.registry.contract.query.view;

/**
 * Dictionary item view object for external subsystem consumption.
 * Used in contract module for clean API boundaries and external system integration.
 * This immutable view object represents dictionary item data optimized for external consumption,
 * excluding internal fields like enabled/deleted status that are not relevant to external systems.
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
     * 
     * @return a string in the format "typeCode:itemCode"
     */
    public String getReferenceString() {
        return typeCode + ":" + itemCode;
    }
    
    /**
     * Creates a DictionaryItemView from a DictionaryItemQuery.
     * Converts query object to view object by excluding internal fields.
     * 
     * @param query the dictionary item query to convert
     * @return a new DictionaryItemView with external-facing fields only
     */
    public static DictionaryItemView fromQuery(DictionaryItemQuery query) {
        return new DictionaryItemView(
            query.typeCode(),
            query.itemCode(),
            query.displayName(),
            query.description(),
            query.isDefault(),
            query.sortOrder()
        );
    }
}