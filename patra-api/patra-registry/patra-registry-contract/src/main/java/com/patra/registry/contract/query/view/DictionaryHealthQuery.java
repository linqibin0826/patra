package com.patra.registry.contract.query.view;

import java.util.Collections;
import java.util.List;

/**
 * Dictionary health status query object for system monitoring.
 * Shared between app module and contract module for health information.
 * This immutable query object represents comprehensive health metrics about the dictionary system,
 * used for monitoring, diagnostics, and health check endpoints.
 * 
 * @param totalTypes total number of dictionary types in the system
 * @param totalItems total number of dictionary items across all types
 * @param enabledItems number of enabled dictionary items available for use
 * @param typesWithoutDefault list of type codes that do not have any default items (potential issue)
 * @param typesWithMultipleDefaults list of type codes that have multiple default items (data integrity issue)
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryHealthQuery(
    int totalTypes,
    int totalItems,
    int enabledItems,
    List<String> typesWithoutDefault,
    List<String> typesWithMultipleDefaults
) {
    
    /**
     * Creates a new DictionaryHealthQuery with validation and immutable collections.
     * 
     * @param totalTypes total number of dictionary types in the system
     * @param totalItems total number of dictionary items across all types
     * @param enabledItems number of enabled dictionary items available for use
     * @param typesWithoutDefault list of type codes that do not have default items
     * @param typesWithMultipleDefaults list of type codes that have multiple default items
     * @throws IllegalArgumentException if any count is negative
     */
    public DictionaryHealthQuery {
        if (totalTypes < 0) {
            throw new IllegalArgumentException("Total types count cannot be negative");
        }
        if (totalItems < 0) {
            throw new IllegalArgumentException("Total items count cannot be negative");
        }
        if (enabledItems < 0) {
            throw new IllegalArgumentException("Enabled items count cannot be negative");
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
     * @return a DictionaryHealthQuery indicating a healthy system
     */
    public static DictionaryHealthQuery healthy(int totalTypes, int totalItems, int enabledItems) {
        return new DictionaryHealthQuery(
            totalTypes, 
            totalItems, 
            enabledItems, 
            Collections.emptyList(), // no types without defaults
            Collections.emptyList()  // no types with multiple defaults
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
     * Gets the number of disabled items (total items minus enabled items).
     * 
     * @return count of items that are disabled
     */
    public int getDisabledItems() {
        return Math.max(0, totalItems - enabledItems);
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
    
    /**
     * Gets all types with integrity issues (both without defaults and with multiple defaults).
     * 
     * @return a list of all type codes that have integrity issues
     */
    public List<String> getAllTypesWithIssues() {
        if (typesWithoutDefault.isEmpty() && typesWithMultipleDefaults.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<String> allIssues = new java.util.ArrayList<>();
        allIssues.addAll(typesWithoutDefault);
        allIssues.addAll(typesWithMultipleDefaults);
        return Collections.unmodifiableList(allIssues);
    }
}