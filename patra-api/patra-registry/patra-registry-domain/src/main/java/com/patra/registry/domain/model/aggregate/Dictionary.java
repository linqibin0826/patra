package com.patra.registry.domain.model.aggregate;

import com.patra.registry.domain.model.vo.DictionaryAlias;
import com.patra.registry.domain.model.vo.DictionaryItem;
import com.patra.registry.domain.model.vo.DictionaryType;
import com.patra.registry.domain.model.vo.ValidationResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Dictionary aggregate root representing dictionary domain concepts for read operations.
 * This aggregate is designed for CQRS query operations only - no command operations are supported.
 * The aggregate encapsulates dictionary type metadata, items, and aliases while providing
 * domain logic for querying, validation, and business rule enforcement.
 * 
 * @author linqibin
 * @since 0.1.0
 */
public class Dictionary {
    
    /** Dictionary type metadata defining the characteristics of this dictionary */
    private final DictionaryType type;
    
    /** List of dictionary items belonging to this type, immutable after construction */
    private final List<DictionaryItem> items;
    
    /** List of external system aliases for dictionary items, immutable after construction */
    private final List<DictionaryAlias> aliases;
    
    /**
     * Creates a new Dictionary aggregate with the provided type, items, and aliases.
     * 
     * @param type the dictionary type metadata, must not be null
     * @param items the list of dictionary items, must not be null (can be empty)
     * @param aliases the list of external aliases, must not be null (can be empty)
     * @throws IllegalArgumentException if type is null
     */
    public Dictionary(DictionaryType type, List<DictionaryItem> items, List<DictionaryAlias> aliases) {
        if (type == null) {
            throw new IllegalArgumentException("Dictionary type cannot be null");
        }
        this.type = type;
        this.items = items != null ? Collections.unmodifiableList(new ArrayList<>(items)) : Collections.emptyList();
        this.aliases = aliases != null ? Collections.unmodifiableList(new ArrayList<>(aliases)) : Collections.emptyList();
    }
    
    /**
     * Creates a new Dictionary aggregate with type and items only (no aliases).
     * 
     * @param type the dictionary type metadata, must not be null
     * @param items the list of dictionary items, must not be null (can be empty)
     * @throws IllegalArgumentException if type is null
     */
    public Dictionary(DictionaryType type, List<DictionaryItem> items) {
        this(type, items, Collections.emptyList());
    }
    
    /**
     * Find dictionary item by item code within this dictionary type.
     * Only returns items that are not deleted (soft-delete aware).
     * 
     * @param itemCode the item code to search for, must not be null or empty
     * @return Optional containing the dictionary item if found and not deleted, empty otherwise
     * @throws IllegalArgumentException if itemCode is null or empty
     */
    public Optional<DictionaryItem> findItemByCode(String itemCode) {
        if (itemCode == null || itemCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Item code cannot be null or empty");
        }
        
        return items.stream()
                .filter(item -> !item.deleted())
                .filter(item -> item.itemCode().equals(itemCode.trim()))
                .findFirst();
    }
    
    /**
     * Find the default dictionary item for this type.
     * Returns the first default item found if multiple exist (data integrity issue).
     * Only considers items that are enabled and not deleted.
     * 
     * @return Optional containing the default item if exists and is available, empty otherwise
     */
    public Optional<DictionaryItem> findDefaultItem() {
        return items.stream()
                .filter(DictionaryItem::isAvailable)
                .filter(DictionaryItem::isDefault)
                .findFirst();
    }
    
    /**
     * Get all enabled dictionary items for this type.
     * Returns items that are enabled and not deleted, sorted by sort_order then item_code.
     * 
     * @return List of enabled dictionary items, sorted by sort_order ascending then item_code ascending
     */
    public List<DictionaryItem> getEnabledItems() {
        return items.stream()
                .filter(DictionaryItem::isAvailable)
                .sorted(Comparator.comparing(DictionaryItem::sortOrder)
                        .thenComparing(DictionaryItem::itemCode))
                .toList();
    }
    
    /**
     * Get all visible dictionary items for this type (not deleted, regardless of enabled status).
     * Returns items sorted by sort_order then item_code.
     * 
     * @return List of visible dictionary items, sorted by sort_order ascending then item_code ascending
     */
    public List<DictionaryItem> getVisibleItems() {
        return items.stream()
                .filter(DictionaryItem::isVisible)
                .sorted(Comparator.comparing(DictionaryItem::sortOrder)
                        .thenComparing(DictionaryItem::itemCode))
                .toList();
    }
    
    /**
     * Validate if an item reference is valid for this dictionary type.
     * An item reference is valid if the item exists, is enabled, and not deleted.
     * 
     * @param itemCode the dictionary item code to validate, must not be null or empty
     * @return ValidationResult indicating success or failure with detailed error message
     * @throws IllegalArgumentException if itemCode is null or empty
     */
    public ValidationResult validateItemReference(String itemCode) {
        if (itemCode == null || itemCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Item code cannot be null or empty");
        }
        
        Optional<DictionaryItem> item = findItemByCode(itemCode);
        if (item.isEmpty()) {
            return ValidationResult.notFound(type.typeCode(), itemCode);
        }
        
        DictionaryItem foundItem = item.get();
        if (!foundItem.enabled()) {
            return ValidationResult.disabled(type.typeCode(), itemCode);
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Find dictionary item by external system alias.
     * Searches through aliases to find matching source system and external code,
     * then returns the corresponding dictionary item if it exists and is available.
     * 
     * @param sourceSystem the external system identifier, must not be null or empty
     * @param externalCode the external system's code, must not be null or empty
     * @return Optional containing the mapped dictionary item if found and available, empty otherwise
     * @throws IllegalArgumentException if sourceSystem or externalCode is null or empty
     */
    public Optional<DictionaryItem> findByAlias(String sourceSystem, String externalCode) {
        if (sourceSystem == null || sourceSystem.trim().isEmpty()) {
            throw new IllegalArgumentException("Source system cannot be null or empty");
        }
        if (externalCode == null || externalCode.trim().isEmpty()) {
            throw new IllegalArgumentException("External code cannot be null or empty");
        }
        
        // Find matching alias
        Optional<DictionaryAlias> matchingAlias = aliases.stream()
                .filter(alias -> alias.sourceSystem().equalsIgnoreCase(sourceSystem.trim()))
                .filter(alias -> alias.externalCode().equals(externalCode.trim()))
                .findFirst();
        
        if (matchingAlias.isEmpty()) {
            return Optional.empty();
        }
        
        // Note: In a real implementation, we would need the item code from the alias
        // For now, this is a placeholder that would need to be completed with proper alias-to-item mapping
        // This would typically involve a repository call or additional data in the alias
        return Optional.empty();
    }
    
    /**
     * Check if this dictionary has any default items.
     * 
     * @return true if at least one enabled item is marked as default, false otherwise
     */
    public boolean hasDefaultItem() {
        return findDefaultItem().isPresent();
    }
    
    /**
     * Check if this dictionary has multiple default items (data integrity issue).
     * 
     * @return true if more than one enabled item is marked as default, false otherwise
     */
    public boolean hasMultipleDefaultItems() {
        long defaultCount = items.stream()
                .filter(DictionaryItem::isAvailable)
                .filter(DictionaryItem::isDefault)
                .count();
        return defaultCount > 1;
    }
    
    /**
     * Get the count of enabled items in this dictionary.
     * 
     * @return the number of items that are enabled and not deleted
     */
    public int getEnabledItemCount() {
        return (int) items.stream()
                .filter(DictionaryItem::isAvailable)
                .count();
    }
    
    /**
     * Get the total count of items in this dictionary (including disabled and deleted).
     * 
     * @return the total number of items in this dictionary
     */
    public int getTotalItemCount() {
        return items.size();
    }
    
    /**
     * Get the dictionary type metadata.
     * 
     * @return the immutable dictionary type information
     */
    public DictionaryType getType() {
        return type;
    }
    
    /**
     * Get all dictionary items (immutable view).
     * 
     * @return immutable list of all dictionary items
     */
    public List<DictionaryItem> getItems() {
        return items;
    }
    
    /**
     * Get all dictionary aliases (immutable view).
     * 
     * @return immutable list of all dictionary aliases
     */
    public List<DictionaryAlias> getAliases() {
        return aliases;
    }
}