package com.patra.registry.domain.model.vo;

/**
 * Dictionary item value object representing individual dictionary entries.
 * This immutable value object encapsulates all information about a dictionary item
 * including its identification, display properties, status, and ordering information.
 * 
 * @param itemCode unique code identifying the dictionary item within its type
 * @param displayName human-readable display name for UI presentation
 * @param description detailed description of the dictionary item purpose and usage
 * @param isDefault whether this item is the default for its type (only one per type should be true)
 * @param sortOrder numeric sort order for display ordering (lower values appear first)
 * @param enabled whether this item is currently enabled for use in business operations
 * @param deleted whether this item has been soft-deleted (should not appear in queries)
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryItem(
    String itemCode,
    String displayName,
    String description,
    boolean isDefault,
    int sortOrder,
    boolean enabled,
    boolean deleted
) {
    
    /**
     * Creates a new DictionaryItem with validation.
     * 
     * @param itemCode unique code identifying the dictionary item within its type
     * @param displayName human-readable display name for UI presentation
     * @param description detailed description of the dictionary item
     * @param isDefault whether this item is the default for its type
     * @param sortOrder numeric sort order for display ordering
     * @param enabled whether this item is currently enabled for use
     * @param deleted whether this item has been soft-deleted
     * @throws IllegalArgumentException if itemCode or displayName is null or empty
     */
    public DictionaryItem {
        if (itemCode == null || itemCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary item code cannot be null or empty");
        }
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary item display name cannot be null or empty");
        }
        // Normalize the codes and names to ensure consistency
        itemCode = itemCode.trim();
        displayName = displayName.trim();
        description = description != null ? description.trim() : "";
    }
    
    /**
     * Checks if this dictionary item is available for use.
     * An item is available if it is enabled and not deleted.
     * 
     * @return true if the item is available for use, false otherwise
     */
    public boolean isAvailable() {
        return enabled && !deleted;
    }
    
    /**
     * Checks if this dictionary item should be visible in queries.
     * Items are visible if they are not deleted (regardless of enabled status).
     * 
     * @return true if the item should be visible, false if deleted
     */
    public boolean isVisible() {
        return !deleted;
    }
    
    /**
     * Checks if this dictionary item can be used as a default value.
     * An item can be default only if it is available (enabled and not deleted).
     * 
     * @return true if this item can serve as a default value, false otherwise
     */
    public boolean canBeDefault() {
        return isAvailable() && isDefault;
    }
    
    /**
     * Creates a copy of this dictionary item with updated enabled status.
     * 
     * @param newEnabled the new enabled status
     * @return a new DictionaryItem with the updated enabled status
     */
    public DictionaryItem withEnabled(boolean newEnabled) {
        return new DictionaryItem(itemCode, displayName, description, isDefault, sortOrder, newEnabled, deleted);
    }
    
    /**
     * Creates a copy of this dictionary item with updated default status.
     * 
     * @param newIsDefault the new default status
     * @return a new DictionaryItem with the updated default status
     */
    public DictionaryItem withDefault(boolean newIsDefault) {
        return new DictionaryItem(itemCode, displayName, description, newIsDefault, sortOrder, enabled, deleted);
    }
}