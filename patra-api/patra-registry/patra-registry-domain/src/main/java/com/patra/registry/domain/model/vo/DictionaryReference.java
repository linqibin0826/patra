package com.patra.registry.domain.model.vo;

/**
 * Dictionary reference value object for validation operations.
 * This immutable value object represents a reference to a specific dictionary item
 * within a dictionary type, used primarily for validation and lookup operations.
 * 
 * @param typeCode the dictionary type code being referenced, must not be null or empty
 * @param itemCode the dictionary item code being referenced, must not be null or empty
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryReference(
    String typeCode,
    String itemCode
) {
    
    /**
     * Creates a new DictionaryReference with validation.
     * 
     * @param typeCode the dictionary type code being referenced
     * @param itemCode the dictionary item code being referenced
     * @throws IllegalArgumentException if typeCode or itemCode is null or empty
     */
    public DictionaryReference {
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary type code cannot be null or empty");
        }
        if (itemCode == null || itemCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary item code cannot be null or empty");
        }
        // Normalize the codes to ensure consistency
        typeCode = typeCode.trim();
        itemCode = itemCode.trim();
    }
    
    /**
     * Creates a dictionary reference from type and item codes.
     * 
     * @param typeCode the dictionary type code
     * @param itemCode the dictionary item code
     * @return a new DictionaryReference instance
     * @throws IllegalArgumentException if typeCode or itemCode is null or empty
     */
    public static DictionaryReference of(String typeCode, String itemCode) {
        return new DictionaryReference(typeCode, itemCode);
    }
    
    /**
     * Gets a string representation of this reference in the format "typeCode:itemCode".
     * 
     * @return a formatted string representation of this dictionary reference
     */
    public String toReferenceString() {
        return typeCode + ":" + itemCode;
    }
    
    /**
     * Checks if this reference matches the given type and item codes.
     * 
     * @param otherTypeCode the type code to compare against
     * @param otherItemCode the item code to compare against
     * @return true if both type and item codes match, false otherwise
     */
    public boolean matches(String otherTypeCode, String otherItemCode) {
        return typeCode.equals(otherTypeCode) && itemCode.equals(otherItemCode);
    }
    
    /**
     * Checks if this reference has the same type code as another reference.
     * 
     * @param other the other dictionary reference to compare
     * @return true if both references have the same type code, false otherwise
     */
    public boolean hasSameType(DictionaryReference other) {
        return other != null && typeCode.equals(other.typeCode);
    }
}