package com.patra.registry.domain.model.vo;

/**
 * Dictionary alias value object for external system integration.
 * This immutable value object represents the mapping between external system codes
 * and internal dictionary items, enabling seamless integration with legacy systems
 * and external data sources.
 * 
 * @param sourceSystem identifier of the external system providing the alias (e.g., "LEGACY_ERP", "EXTERNAL_API")
 * @param externalCode the external system's code for this dictionary item
 * @param externalLabel the external system's human-readable label for this dictionary item
 * @param notes additional notes about the alias mapping, transformation rules, or usage context
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryAlias(
    String sourceSystem,
    String externalCode,
    String externalLabel,
    String notes
) {
    
    /**
     * Creates a new DictionaryAlias with validation.
     * 
     * @param sourceSystem identifier of the external system providing the alias
     * @param externalCode the external system's code for this dictionary item
     * @param externalLabel the external system's label for this dictionary item
     * @param notes additional notes about the alias mapping
     * @throws IllegalArgumentException if sourceSystem or externalCode is null or empty
     */
    public DictionaryAlias {
        if (sourceSystem == null || sourceSystem.trim().isEmpty()) {
            throw new IllegalArgumentException("Source system cannot be null or empty");
        }
        if (externalCode == null || externalCode.trim().isEmpty()) {
            throw new IllegalArgumentException("External code cannot be null or empty");
        }
        // Normalize the values to ensure consistency
        sourceSystem = sourceSystem.trim().toUpperCase();
        externalCode = externalCode.trim();
        externalLabel = externalLabel != null ? externalLabel.trim() : "";
        notes = notes != null ? notes.trim() : "";
    }
    
    /**
     * Checks if this alias has a meaningful external label.
     * 
     * @return true if the external label is not null and not empty, false otherwise
     */
    public boolean hasExternalLabel() {
        return externalLabel != null && !externalLabel.isEmpty();
    }
    
    /**
     * Checks if this alias has additional notes.
     * 
     * @return true if notes are provided, false otherwise
     */
    public boolean hasNotes() {
        return notes != null && !notes.isEmpty();
    }
    
    /**
     * Gets the display label for this alias, preferring external label over external code.
     * 
     * @return the external label if available, otherwise the external code
     */
    public String getDisplayLabel() {
        return hasExternalLabel() ? externalLabel : externalCode;
    }
    
    /**
     * Creates a unique key for this alias based on source system and external code.
     * This key can be used for deduplication and lookup operations.
     * 
     * @return a unique string key combining source system and external code
     */
    public String getUniqueKey() {
        return sourceSystem + ":" + externalCode;
    }
}