package com.patra.registry.domain.port;

import com.patra.registry.domain.exception.DictionaryRepositoryException;
import com.patra.registry.domain.model.aggregate.Dictionary;
import com.patra.registry.domain.model.vo.DictionaryHealthStatus;
import com.patra.registry.domain.model.vo.DictionaryItem;
import com.patra.registry.domain.model.vo.DictionaryType;

import java.util.List;
import java.util.Optional;

/**
 * Domain repository port for dictionary read operations.
 * This interface defines the contract for dictionary data access in the CQRS query model.
 * All operations are read-only - no command operations are supported.
 * 
 * The repository provides access to dictionary aggregates, individual items, types,
 * and system health information while maintaining clean hexagonal architecture boundaries.
 * 
 * @author linqibin
 * @since 0.1.0
 */
public interface DictionaryRepository {
    
    /**
     * Find dictionary aggregate by type code.
     * Returns the complete dictionary aggregate including type metadata, items, and aliases.
     * Only includes items that are not soft-deleted.
     * 
     * @param typeCode the dictionary type code to search for, must not be null or empty
     * @return Optional containing the dictionary aggregate if found, empty otherwise
     * @throws IllegalArgumentException if typeCode is null or empty
     * @throws DictionaryRepositoryException if data access fails
     */
    Optional<Dictionary> findByTypeCode(String typeCode);
    
    /**
     * Find all dictionary types in the system.
     * Returns all dictionary types ordered by type_code for consistent results.
     * Includes both system-managed and user-managed types.
     * 
     * @return List of all dictionary types, ordered by type_code ascending, never null
     * @throws DictionaryRepositoryException if data access fails
     */
    List<DictionaryType> findAllTypes();
    
    /**
     * Find specific dictionary item by type and item code.
     * Returns the dictionary item if it exists and is not soft-deleted.
     * This is an optimized query for single item lookup without loading the full aggregate.
     * 
     * @param typeCode the dictionary type code, must not be null or empty
     * @param itemCode the dictionary item code, must not be null or empty
     * @return Optional containing the dictionary item if found and not deleted, empty otherwise
     * @throws IllegalArgumentException if typeCode or itemCode is null or empty
     * @throws DictionaryRepositoryException if data access fails
     */
    Optional<DictionaryItem> findItemByTypeAndCode(String typeCode, String itemCode);
    
    /**
     * Find the default dictionary item for a given type.
     * Returns the item marked as default that is enabled and not deleted.
     * If multiple default items exist (data integrity issue), returns the first one found.
     * 
     * @param typeCode the dictionary type code, must not be null or empty
     * @return Optional containing the default item if exists and is available, empty otherwise
     * @throws IllegalArgumentException if typeCode is null or empty
     * @throws DictionaryRepositoryException if data access fails
     */
    Optional<DictionaryItem> findDefaultItemByType(String typeCode);
    
    /**
     * Find all enabled dictionary items for a given type.
     * Returns items that are enabled and not deleted, sorted by sort_order then item_code.
     * This is an optimized query for dropdown population and selection lists.
     * 
     * @param typeCode the dictionary type code, must not be null or empty
     * @return List of enabled items, sorted by sort_order ascending then item_code ascending, never null
     * @throws IllegalArgumentException if typeCode is null or empty
     * @throws DictionaryRepositoryException if data access fails
     */
    List<DictionaryItem> findEnabledItemsByType(String typeCode);
    
    /**
     * Find dictionary item by external system alias.
     * Searches through alias mappings to find the corresponding internal dictionary item.
     * Returns the item only if it exists, is enabled, and not deleted.
     * 
     * @param sourceSystem the external system identifier, must not be null or empty
     * @param externalCode the external system's code, must not be null or empty
     * @return Optional containing the mapped dictionary item if found and available, empty otherwise
     * @throws IllegalArgumentException if sourceSystem or externalCode is null or empty
     * @throws DictionaryRepositoryException if data access fails
     */
    Optional<DictionaryItem> findByAlias(String sourceSystem, String externalCode);
    
    /**
     * Get dictionary system health status for monitoring and diagnostics.
     * Provides comprehensive metrics about dictionary types, items, and data integrity issues.
     * This operation may be expensive as it aggregates data across all dictionary tables.
     * 
     * @return DictionaryHealthStatus containing system health metrics and integrity issues
     * @throws DictionaryRepositoryException if data access fails
     */
    DictionaryHealthStatus getHealthStatus();
    
    /**
     * Check if a dictionary type exists in the system.
     * This is a lightweight operation for existence checking without loading full data.
     * 
     * @param typeCode the dictionary type code to check, must not be null or empty
     * @return true if the dictionary type exists, false otherwise
     * @throws IllegalArgumentException if typeCode is null or empty
     * @throws DictionaryRepositoryException if data access fails
     */
    boolean existsByTypeCode(String typeCode);
    
    /**
     * Check if a dictionary item exists for the given type and item codes.
     * This is a lightweight operation for existence checking without loading full data.
     * Only considers items that are not soft-deleted.
     * 
     * @param typeCode the dictionary type code, must not be null or empty
     * @param itemCode the dictionary item code, must not be null or empty
     * @return true if the dictionary item exists and is not deleted, false otherwise
     * @throws IllegalArgumentException if typeCode or itemCode is null or empty
     * @throws DictionaryRepositoryException if data access fails
     */
    boolean existsByTypeAndItemCode(String typeCode, String itemCode);
    
    /**
     * Get the count of enabled items for a specific dictionary type.
     * This is an optimized count query without loading the actual items.
     * 
     * @param typeCode the dictionary type code, must not be null or empty
     * @return count of enabled items for the specified type, 0 if type doesn't exist
     * @throws IllegalArgumentException if typeCode is null or empty
     * @throws DictionaryRepositoryException if data access fails
     */
    int countEnabledItemsByType(String typeCode);
    
    /**
     * Find dictionary types that have no default items.
     * This is used for health monitoring to identify potential configuration issues.
     * 
     * @return List of type codes that do not have any default items, never null
     * @throws DictionaryRepositoryException if data access fails
     */
    List<String> findTypesWithoutDefaults();
    
    /**
     * Find dictionary types that have multiple default items.
     * This is used for health monitoring to identify data integrity issues.
     * 
     * @return List of type codes that have more than one default item, never null
     * @throws DictionaryRepositoryException if data access fails
     */
    List<String> findTypesWithMultipleDefaults();
}