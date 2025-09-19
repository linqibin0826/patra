package com.patra.registry.domain.model.vo;

import java.util.Collections;
import java.util.List;

/**
 * Dictionary health status value object for system monitoring and diagnostics.
 * This immutable value object encapsulates comprehensive health metrics about the dictionary system,
 * including counts, integrity issues, and potential problems that require attention.
 * 
 * @param totalTypes total number of dictionary types in the system
 * @param totalItems total number of dictionary items across all types
 * @param enabledItems number of dictionary items that are currently enabled and available
 * @param deletedItems number of dictionary items that have been soft-deleted
 * @param typesWithoutDefault list of type codes that do not have any default items (potential issue)
 * @param typesWithMultipleDefaults list of type codes that have multiple default items (data integrity issue)
 * @param disabledTypes number of dictionary types that are currently disabled
 * @param systemTypes number of dictionary types that are system-managed (read-only)
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryHealthStatus(
    int totalTypes,
    int totalItems,
    int enabledItems,
    int deletedItems,
    List<String> typesWithoutDefault,
    List<String> typesWithMultipleDefaults,
    int disabledTypes,
    int systemTypes
) {
    
    /**
     * Creates a new DictionaryHealthStatus with validation and immutable collections.
     * 
     * @param totalTypes total number of dictionary types in the system
     * @param totalItems total number of dictionary items across all types
     * @param enabledItems number of dictionary items that are currently enabled
     * @param deletedItems number of dictionary items that have been soft-deleted
     * @param typesWithoutDefault list of type codes without default items
     * @param typesWithMultipleDefaults list of type codes with multiple default items
     * @param disabledTypes number of dictionary types that are currently disabled
     * @param systemTypes number of dictionary types that are system-managed
     * @throws IllegalArgumentException if any count is negative
     */
    public DictionaryHealthStatus {
        if (totalTypes < 0) {
            throw new IllegalArgumentException("Total types count cannot be negative");
        }
        if (totalItems < 0) {
            throw new IllegalArgumentException("Total items count cannot be negative");
        }
        if (enabledItems < 0) {
            throw new IllegalArgumentException("Enabled items count cannot be negative");
        }
        if (deletedItems < 0) {
            throw new IllegalArgumentException("Deleted items count cannot be negative");
        }
        if (disabledTypes < 0) {
            throw new IllegalArgumentException("Disabled types count cannot be negative");
        }
        if (systemTypes < 0) {
            throw new IllegalArgumentException("System types count cannot be negative");
        }
        
        // Ensure immutable collections
        typesWithoutDefault = typesWithoutDefault != null ? 
            Collections.unmodifiableList(List.copyOf(typesWithoutDefault)) : 
            Collections.emptyList();
        typesWithMultipleDefaults = typesWithMultipleDefaults != null ? 
            Collections.unmodifiableList(List.copyOf(typesWithMultipleDefaults)) : 
            Collections.emptyList();
    }
    
    /**
     * Creates a healthy dictionary status with no issues.
     * 
     * @param totalTypes total number of dictionary types
     * @param totalItems total number of dictionary items
     * @param enabledItems number of enabled dictionary items
     * @param systemTypes number of system-managed types
     * @return a DictionaryHealthStatus indicating a healthy system
     */
    public static DictionaryHealthStatus healthy(int totalTypes, int totalItems, int enabledItems, int systemTypes) {
        return new DictionaryHealthStatus(
            totalTypes, 
            totalItems, 
            enabledItems, 
            0, // no deleted items
            Collections.emptyList(), // no types without defaults
            Collections.emptyList(), // no types with multiple defaults
            0, // no disabled types
            systemTypes
        );
    }
    
    /**
     * Checks if the dictionary system is in a healthy state.
     * A system is considered healthy if there are no integrity issues.
     * 
     * @return true if no integrity issues are detected, false otherwise
     */
    public boolean isHealthy() {
        return typesWithoutDefault.isEmpty() && typesWithMultipleDefaults.isEmpty();
    }
    
    /**
     * Checks if there are any data integrity issues.
     * 
     * @return true if integrity issues are detected, false otherwise
     */
    public boolean hasIntegrityIssues() {
        return !isHealthy();
    }
    
    /**
     * Gets the number of dictionary types with integrity issues.
     * 
     * @return count of types that have either no default or multiple defaults
     */
    public int getTypesWithIssuesCount() {
        return typesWithoutDefault.size() + typesWithMultipleDefaults.size();
    }
    
    /**
     * Gets the number of disabled items (total items minus enabled items minus deleted items).
     * 
     * @return count of items that are disabled but not deleted
     */
    public int getDisabledItems() {
        return Math.max(0, totalItems - enabledItems - deletedItems);
    }
    
    /**
     * Gets the percentage of enabled items out of total items.
     * 
     * @return percentage of enabled items (0.0 to 100.0), or 0.0 if no items exist
     */
    public double getEnabledItemsPercentage() {
        if (totalItems == 0) {
            return 0.0;
        }
        return (double) enabledItems / totalItems * 100.0;
    }
    
    /**
     * Gets the number of user-managed dictionary types (non-system types).
     * 
     * @return count of dictionary types that are not system-managed
     */
    public int getUserTypes() {
        return Math.max(0, totalTypes - systemTypes);
    }
    
    /**
     * Checks if there are any types without default items.
     * 
     * @return true if some types are missing default items, false otherwise
     */
    public boolean hasTypesWithoutDefaults() {
        return !typesWithoutDefault.isEmpty();
    }
    
    /**
     * Checks if there are any types with multiple default items.
     * 
     * @return true if some types have multiple default items, false otherwise
     */
    public boolean hasTypesWithMultipleDefaults() {
        return !typesWithMultipleDefaults.isEmpty();
    }
    
    /**
     * Gets a summary description of the health status.
     * 
     * @return a human-readable summary of the dictionary system health
     */
    public String getHealthSummary() {
        if (isHealthy()) {
            return String.format("Healthy: %d types, %d items (%d enabled)", 
                totalTypes, totalItems, enabledItems);
        } else {
            return String.format("Issues detected: %d types with problems out of %d total types", 
                getTypesWithIssuesCount(), totalTypes);
        }
    }
}