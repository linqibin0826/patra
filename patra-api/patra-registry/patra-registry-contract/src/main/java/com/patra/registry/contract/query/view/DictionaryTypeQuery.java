package com.patra.registry.contract.query.view;

/**
 * Dictionary type query object for CQRS read operations.
 * Shared between app module and contract module for consistent data structure.
 * This immutable query object represents dictionary type metadata optimized for read operations
 * and external subsystem consumption via API contracts.
 * 
 * @param typeCode unique dictionary type code identifier
 * @param typeName human-readable type name for display purposes
 * @param description detailed type description explaining purpose and usage
 * @param enabledItemCount number of enabled items available in this type
 * @param hasDefault whether this type has a default item configured
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryTypeQuery(
    String typeCode,
    String typeName,
    String description,
    int enabledItemCount,
    boolean hasDefault
) {
    
    /**
     * Creates a new DictionaryTypeQuery with validation.
     * 
     * @param typeCode unique dictionary type code identifier
     * @param typeName human-readable type name for display purposes
     * @param description detailed type description explaining purpose and usage
     * @param enabledItemCount number of enabled items available in this type
     * @param hasDefault whether this type has a default item configured
     * @throws IllegalArgumentException if typeCode or typeName is null or empty, or if enabledItemCount is negative
     */
    public DictionaryTypeQuery {
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary type code cannot be null or empty");
        }
        if (typeName == null || typeName.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary type name cannot be null or empty");
        }
        if (enabledItemCount < 0) {
            throw new IllegalArgumentException("Enabled item count cannot be negative");
        }
        
        // Normalize the codes and names to ensure consistency
        typeCode = typeCode.trim();
        typeName = typeName.trim();
        description = description != null ? description.trim() : "";
    }
    
    /**
     * Checks if this dictionary type has any enabled items available.
     * 
     * @return true if there are enabled items in this type, false otherwise
     */
    public boolean hasEnabledItems() {
        return enabledItemCount > 0;
    }
    
    /**
     * Checks if this dictionary type is properly configured.
     * A type is considered properly configured if it has enabled items and a default item.
     * 
     * @return true if the type has enabled items and a default item, false otherwise
     */
    public boolean isProperlyConfigured() {
        return hasEnabledItems() && hasDefault;
    }
    
    /**
     * Checks if this dictionary type has configuration issues.
     * Issues include having no enabled items or missing a default item when items exist.
     * 
     * @return true if configuration issues are detected, false otherwise
     */
    public boolean hasConfigurationIssues() {
        return !hasEnabledItems() || (hasEnabledItems() && !hasDefault);
    }
    
    /**
     * Gets a summary description of this dictionary type's status.
     * 
     * @return a human-readable summary of the type's configuration status
     */
    public String getStatusSummary() {
        if (!hasEnabledItems()) {
            return "No enabled items";
        } else if (!hasDefault) {
            return String.format("%d items, no default", enabledItemCount);
        } else {
            return String.format("%d items, has default", enabledItemCount);
        }
    }
}