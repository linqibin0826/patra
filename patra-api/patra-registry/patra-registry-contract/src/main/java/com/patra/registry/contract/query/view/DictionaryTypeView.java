package com.patra.registry.contract.query.view;

/**
 * Dictionary type view object for external subsystem consumption.
 * Used in contract module for clean API boundaries and external system integration.
 * This immutable view object represents dictionary type metadata optimized for external consumption,
 * providing essential type information without internal system details.
 * 
 * @param typeCode unique dictionary type code identifier
 * @param typeName human-readable type name for display purposes
 * @param description detailed type description explaining purpose and usage
 * @param itemCount number of available items in this type (enabled items only)
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryTypeView(
    String typeCode,
    String typeName,
    String description,
    int itemCount
) {
    
    /**
     * Creates a new DictionaryTypeView with validation.
     * 
     * @param typeCode unique dictionary type code identifier
     * @param typeName human-readable type name for display purposes
     * @param description detailed type description explaining purpose and usage
     * @param itemCount number of available items in this type
     * @throws IllegalArgumentException if typeCode or typeName is null or empty, or if itemCount is negative
     */
    public DictionaryTypeView {
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary type code cannot be null or empty");
        }
        if (typeName == null || typeName.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary type name cannot be null or empty");
        }
        if (itemCount < 0) {
            throw new IllegalArgumentException("Item count cannot be negative");
        }
        
        // Normalize the codes and names to ensure consistency
        typeCode = typeCode.trim();
        typeName = typeName.trim();
        description = description != null ? description.trim() : "";
    }
    
    /**
     * Checks if this dictionary type has any available items.
     * 
     * @return true if there are items available in this type, false otherwise
     */
    public boolean hasItems() {
        return itemCount > 0;
    }
    
    /**
     * Creates a DictionaryTypeView from a DictionaryTypeQuery.
     * Converts query object to view object for external consumption.
     * 
     * @param query the dictionary type query to convert
     * @return a new DictionaryTypeView with external-facing fields only
     */
    public static DictionaryTypeView fromQuery(DictionaryTypeQuery query) {
        return new DictionaryTypeView(
            query.typeCode(),
            query.typeName(),
            query.description(),
            query.enabledItemCount()
        );
    }
}