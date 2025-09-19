package com.patra.registry.domain.model.vo;

/**
 * Dictionary type value object representing dictionary type metadata.
 * This immutable value object encapsulates all information about a dictionary type
 * including its identification, display properties, and behavioral characteristics.
 * 
 * @param typeCode unique code identifying the dictionary type, must not be null or empty
 * @param typeName human-readable name of the dictionary type for display purposes
 * @param description detailed description of the dictionary type purpose and usage
 * @param allowCustomItems whether this type allows custom items to be added by users
 * @param isSystem whether this is a system-managed dictionary type (read-only for users)
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryType(
    String typeCode,
    String typeName,
    String description,
    boolean allowCustomItems,
    boolean isSystem
) {
    
    /**
     * Creates a new DictionaryType with validation.
     * 
     * @param typeCode unique code identifying the dictionary type
     * @param typeName human-readable name of the dictionary type
     * @param description detailed description of the dictionary type purpose
     * @param allowCustomItems whether this type allows custom items to be added
     * @param isSystem whether this is a system-managed dictionary type
     * @throws IllegalArgumentException if typeCode or typeName is null or empty
     */
    public DictionaryType {
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary type code cannot be null or empty");
        }
        if (typeName == null || typeName.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary type name cannot be null or empty");
        }
        // Normalize the typeCode to ensure consistency
        typeCode = typeCode.trim();
        typeName = typeName.trim();
        description = description != null ? description.trim() : "";
    }
    
    /**
     * Checks if this dictionary type is editable by users.
     * System dictionary types are typically read-only for users.
     * 
     * @return true if the dictionary type can be modified by users, false otherwise
     */
    public boolean isEditable() {
        return !isSystem;
    }
    
    /**
     * Checks if this dictionary type supports custom item creation.
     * 
     * @return true if custom items can be added to this type, false otherwise
     */
    public boolean supportsCustomItems() {
        return allowCustomItems;
    }
}