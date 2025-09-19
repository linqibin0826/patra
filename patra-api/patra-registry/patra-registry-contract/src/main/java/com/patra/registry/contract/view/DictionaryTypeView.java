package com.patra.registry.contract.view;

/**
 * Dictionary type view object for external subsystem consumption.
 * Used in contract module for clean API boundaries and external system integration.
 * This immutable view object provides a simplified representation of dictionary types
 * optimized for external consumption without internal implementation details.
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
     * Checks if this dictionary type has any items available.
     * 
     * @return true if there are items in this type, false otherwise
     */
    public boolean hasItems() {
        return itemCount > 0;
    }
    
    /**
     * Checks if this dictionary type has a meaningful description.
     * 
     * @return true if description is not null and not empty, false otherwise
     */
    public boolean hasDescription() {
        return description != null && !description.trim().isEmpty();
    }
    
    /**
     * Gets a display-friendly label combining the type code and type name.
     * Useful for dropdown lists and selection components in external systems.
     * 
     * @return a string in the format "typeCode - typeName"
     */
    public String getDisplayLabel() {
        return typeCode + " - " + typeName;
    }
    
    /**
     * Gets a summary description of this dictionary type including item count.
     * 
     * @return a human-readable summary including type name and item count
     */
    public String getSummary() {
        if (itemCount == 0) {
            return typeName + " (no items)";
        } else if (itemCount == 1) {
            return typeName + " (1 item)";
        } else {
            return typeName + " (" + itemCount + " items)";
        }
    }
    
    /**
     * Creates a simplified view with only essential fields for basic display.
     * Useful when only basic information is needed for external system integration.
     * 
     * @return a new DictionaryTypeView with empty description
     */
    public DictionaryTypeView toSimplifiedView() {
        return new DictionaryTypeView(typeCode, typeName, "", itemCount);
    }
    
    /**
     * Checks if this dictionary type is suitable for use in external systems.
     * A type is suitable if it has a meaningful name and at least one item.
     * 
     * @return true if the type is suitable for external use, false otherwise
     */
    public boolean isSuitableForExternalUse() {
        return hasItems() && typeName != null && !typeName.trim().isEmpty();
    }
}