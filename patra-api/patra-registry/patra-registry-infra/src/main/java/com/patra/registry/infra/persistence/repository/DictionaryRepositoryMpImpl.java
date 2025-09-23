package com.patra.registry.infra.persistence.repository;

import com.patra.registry.domain.model.aggregate.Dictionary;
import com.patra.registry.domain.model.vo.DictionaryAlias;
import com.patra.registry.domain.model.vo.DictionaryHealthStatus;
import com.patra.registry.domain.model.vo.DictionaryId;
import com.patra.registry.domain.model.vo.DictionaryItem;
import com.patra.registry.domain.model.vo.DictionaryType;
import com.patra.registry.domain.port.DictionaryRepository;
import com.patra.registry.domain.exception.dictionary.DictionaryRepositoryException;
import com.patra.registry.infra.mapstruct.DictionaryEntityConverter;
import com.patra.registry.infra.persistence.entity.dictionary.RegSysDictItemAliasDO;
import com.patra.registry.infra.persistence.entity.dictionary.RegSysDictItemDO;
import com.patra.registry.infra.persistence.entity.dictionary.RegSysDictTypeDO;
import com.patra.registry.infra.persistence.mapper.RegSysDictItemAliasMapper;
import com.patra.registry.infra.persistence.mapper.RegSysDictItemMapper;
import com.patra.registry.infra.persistence.mapper.RegSysDictTypeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 基于 MyBatis-Plus 的字典查询侧仓储实现。
 *
 * <p>提供面向 CQRS 查询侧的高效数据访问，使用 MapStruct 完成实体到领域对象的转换，
 * 并通过结构化日志便于排障与观测。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class DictionaryRepositoryMpImpl implements DictionaryRepository {
    
    /** 类型 Mapper */
    private final RegSysDictTypeMapper typeMapper;
    
    /** 项 Mapper */
    private final RegSysDictItemMapper itemMapper;
    
    /** 别名 Mapper */
    private final RegSysDictItemAliasMapper aliasMapper;
    
    /** 实体 -> 领域 转换器 */
    private final DictionaryEntityConverter entityConverter;
    
    /**
     * Find dictionary aggregate by type code.
     * Loads the complete dictionary aggregate including type metadata, items, and aliases.
     * Uses optimized queries and handles entity to domain conversion with proper error handling.
     * 
     * @param typeCode the dictionary type code to search for, must not be null or empty
     * @return Optional containing the dictionary aggregate if found, empty otherwise
     * @throws IllegalArgumentException if typeCode is null or empty
     * @throws DictionaryRepositoryException if data access fails
     */
    @Override
    public Optional<Dictionary> findByTypeCode(String typeCode) {
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Type code cannot be null or empty");
        }
        
        log.debug("Repository: Finding dictionary aggregate by type code: typeCode={}", typeCode);
        
        try {
            // Find dictionary type
            Optional<RegSysDictTypeDO> typeEntity = typeMapper.selectByTypeCode(typeCode);
            if (typeEntity.isEmpty()) {
                log.debug("Repository: Dictionary type not found: typeCode={}", typeCode);
                return Optional.empty();
            }
            
            // Convert type entity to domain object
            DictionaryType type = entityConverter.toDomain(typeEntity.get());
            log.debug("Repository: Successfully converted type entity to domain: typeCode={}", typeCode);
            
            // Find dictionary items for this type
            List<RegSysDictItemDO> itemEntities = itemMapper.selectEnabledByTypeId(typeEntity.get().getId());
            List<DictionaryItem> items = entityConverter.toItemDomainList(itemEntities);
            log.debug("Repository: Found {} items for type: typeCode={}", items.size(), typeCode);
            
            // Find aliases for items of this type
            List<RegSysDictItemAliasDO> aliasEntities = aliasMapper.selectByTypeCode(typeCode);
            List<DictionaryAlias> aliases = entityConverter.toAliasDomainList(aliasEntities);
            log.debug("Repository: Found {} aliases for type: typeCode={}", aliases.size(), typeCode);
            
            // Create and return dictionary aggregate using record constructor
            Dictionary dictionary = new Dictionary(DictionaryId.of(type), type, items, aliases);
            log.debug("Repository: Successfully created dictionary aggregate: typeCode={}, itemCount={}, aliasCount={}",
                     typeCode, items.size(), aliases.size());
            return Optional.of(dictionary);
            
        } catch (Exception e) {
            log.error("Repository: Error finding dictionary aggregate: typeCode={}, error={}", 
                     typeCode, e.getMessage(), e);
            throw new DictionaryRepositoryException("Failed to find dictionary aggregate for type: " + typeCode, e);
        }
    }
    
    /**
     * Find all dictionary types in the system.
     * Returns all dictionary types ordered by type_code for consistent results.
     * 
     * @return List of all dictionary types, ordered by type_code ascending, never null
     * @throws DictionaryRepositoryException if data access fails
     */
    @Override
    public List<DictionaryType> findAllTypes() {
        log.debug("Repository: Finding all dictionary types");
        
        try {
            List<RegSysDictTypeDO> typeEntities = typeMapper.selectAllEnabled();
            List<DictionaryType> types = entityConverter.toDomainList(typeEntities);
            
            log.debug("Repository: Successfully found {} dictionary types", types.size());
            return types;
            
        } catch (Exception e) {
            log.error("Repository: Error finding all dictionary types: error={}", e.getMessage(), e);
            throw new DictionaryRepositoryException("Failed to find all dictionary types", e);
        }
    }
    
    /**
     * Find specific dictionary item by type and item code.
     * Uses optimized query for single item lookup without loading the full aggregate.
     * 
     * @param typeCode the dictionary type code, must not be null or empty
     * @param itemCode the dictionary item code, must not be null or empty
     * @return Optional containing the dictionary item if found and not deleted, empty otherwise
     * @throws IllegalArgumentException if typeCode or itemCode is null or empty
     * @throws DictionaryRepositoryException if data access fails
     */
    @Override
    public Optional<DictionaryItem> findItemByTypeAndCode(String typeCode, String itemCode) {
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Type code cannot be null or empty");
        }
        if (itemCode == null || itemCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Item code cannot be null or empty");
        }
        
        log.debug("Repository: Finding dictionary item by type and code: typeCode={}, itemCode={}", typeCode, itemCode);
        
        try {
            Optional<RegSysDictItemDO> entity = itemMapper.selectByTypeAndItemCode(typeCode, itemCode);
            if (entity.isEmpty()) {
                log.debug("Repository: Dictionary item not found in database: typeCode={}, itemCode={}", typeCode, itemCode);
                return Optional.empty();
            }
            
            DictionaryItem domainItem = entityConverter.toDomain(entity.get());
            log.debug("Repository: Successfully converted entity to domain: typeCode={}, itemCode={}", typeCode, itemCode);
            return Optional.of(domainItem);
            
        } catch (Exception e) {
            log.error("Repository: Error finding dictionary item: typeCode={}, itemCode={}, error={}", 
                     typeCode, itemCode, e.getMessage(), e);
            throw new DictionaryRepositoryException("Failed to find dictionary item", e);
        }
    }
    
    /**
     * Find the default dictionary item for a given type.
     * Returns the item marked as default that is enabled and not deleted.
     * 
     * @param typeCode the dictionary type code, must not be null or empty
     * @return Optional containing the default item if exists and is available, empty otherwise
     * @throws IllegalArgumentException if typeCode is null or empty
     * @throws DictionaryRepositoryException if data access fails
     */
    @Override
    public Optional<DictionaryItem> findDefaultItemByType(String typeCode) {
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Type code cannot be null or empty");
        }
        
        log.debug("Repository: Finding default dictionary item by type: typeCode={}", typeCode);
        
        try {
            Optional<RegSysDictItemDO> entity = itemMapper.selectDefaultByTypeCode(typeCode);
            if (entity.isEmpty()) {
                log.debug("Repository: No default dictionary item found: typeCode={}", typeCode);
                return Optional.empty();
            }
            
            DictionaryItem domainItem = entityConverter.toDomain(entity.get());
            log.debug("Repository: Successfully found default dictionary item: typeCode={}, itemCode={}", 
                     typeCode, domainItem.itemCode());
            return Optional.of(domainItem);
            
        } catch (Exception e) {
            log.error("Repository: Error finding default dictionary item: typeCode={}, error={}", 
                     typeCode, e.getMessage(), e);
            throw new DictionaryRepositoryException("Failed to find default dictionary item for type: " + typeCode, e);
        }
    }
    
    /**
     * Find all enabled dictionary items for a given type.
     * Returns items that are enabled and not deleted, sorted by sort_order then item_code.
     * 
     * @param typeCode the dictionary type code, must not be null or empty
     * @return List of enabled items, sorted by sort_order ascending then item_code ascending, never null
     * @throws IllegalArgumentException if typeCode is null or empty
     * @throws DictionaryRepositoryException if data access fails
     */
    @Override
    public List<DictionaryItem> findEnabledItemsByType(String typeCode) {
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Type code cannot be null or empty");
        }
        
        log.debug("Repository: Finding enabled dictionary items by type: typeCode={}", typeCode);
        
        try {
            List<RegSysDictItemDO> entities = itemMapper.selectEnabledByTypeCode(typeCode);
            List<DictionaryItem> items = entityConverter.toItemDomainList(entities);
            
            log.debug("Repository: Successfully found {} enabled dictionary items: typeCode={}", items.size(), typeCode);
            return items;
            
        } catch (Exception e) {
            log.error("Repository: Error finding enabled dictionary items: typeCode={}, error={}", 
                     typeCode, e.getMessage(), e);
            throw new DictionaryRepositoryException("Failed to find enabled dictionary items for type: " + typeCode, e);
        }
    }
    
    /**
     * Find dictionary item by external system alias.
     * Searches through alias mappings to find the corresponding internal dictionary item.
     * 
     * @param sourceSystem the external system identifier, must not be null or empty
     * @param externalCode the external system's code, must not be null or empty
     * @return Optional containing the mapped dictionary item if found and available, empty otherwise
     * @throws IllegalArgumentException if sourceSystem or externalCode is null or empty
     * @throws DictionaryRepositoryException if data access fails
     */
    @Override
    public Optional<DictionaryItem> findByAlias(String sourceSystem, String externalCode) {
        if (sourceSystem == null || sourceSystem.trim().isEmpty()) {
            throw new IllegalArgumentException("Source system cannot be null or empty");
        }
        if (externalCode == null || externalCode.trim().isEmpty()) {
            throw new IllegalArgumentException("External code cannot be null or empty");
        }
        
        log.debug("Repository: Finding dictionary item by alias: sourceSystem={}, externalCode={}", 
                 sourceSystem, externalCode);
        
        try {
            Optional<RegSysDictItemDO> entity = aliasMapper.selectItemByAlias(sourceSystem, externalCode);
            if (entity.isEmpty()) {
                log.debug("Repository: No dictionary item found for alias: sourceSystem={}, externalCode={}", 
                         sourceSystem, externalCode);
                return Optional.empty();
            }
            
            DictionaryItem domainItem = entityConverter.toDomain(entity.get());
            log.debug("Repository: Successfully found dictionary item by alias: sourceSystem={}, externalCode={}, itemCode={}", 
                     sourceSystem, externalCode, domainItem.itemCode());
            return Optional.of(domainItem);
            
        } catch (Exception e) {
            log.error("Repository: Error finding dictionary item by alias: sourceSystem={}, externalCode={}, error={}", 
                     sourceSystem, externalCode, e.getMessage(), e);
            throw new DictionaryRepositoryException("Failed to find dictionary item by alias", e);
        }
    }
    
    /**
     * Get dictionary system health status for monitoring and diagnostics.
     * Provides comprehensive metrics about dictionary types, items, and data integrity issues.
     * 
     * @return DictionaryHealthStatus containing system health metrics and integrity issues
     * @throws DictionaryRepositoryException if data access fails
     */
    @Override
    public DictionaryHealthStatus getHealthStatus() {
        log.debug("Repository: Getting dictionary system health status");
        
        try {
            // Get basic counts
            int totalTypes = typeMapper.countTotal();
            int totalItems = itemMapper.countTotal();
            int enabledItems = itemMapper.countTotalEnabled();
            int systemTypes = typeMapper.selectSystemTypes().size();
            
            // Get integrity issues
            List<String> typesWithoutDefaults = itemMapper.selectTypesWithoutDefaults();
            List<String> typesWithMultipleDefaults = itemMapper.selectTypesWithMultipleDefaults();
            
            // Calculate derived metrics
            int deletedItems = 0; // This would need a separate query if we track deleted items
            int disabledTypes = 0; // This would need a separate query if we track disabled types
            
            DictionaryHealthStatus healthStatus = new DictionaryHealthStatus(
                totalTypes,
                totalItems,
                enabledItems,
                deletedItems,
                typesWithoutDefaults,
                typesWithMultipleDefaults,
                disabledTypes,
                systemTypes
            );
            
            log.info("Repository: Dictionary health status - totalTypes={}, totalItems={}, enabledItems={}, issueTypes={}", 
                    totalTypes, totalItems, enabledItems, healthStatus.getTypesWithIssuesCount());
            
            if (healthStatus.hasIntegrityIssues()) {
                log.warn("Repository: Dictionary integrity issues detected - typesWithoutDefaults={}, typesWithMultipleDefaults={}", 
                        typesWithoutDefaults.size(), typesWithMultipleDefaults.size());
            }
            
            return healthStatus;
            
        } catch (Exception e) {
            log.error("Repository: Error getting dictionary health status: error={}", e.getMessage(), e);
            throw new DictionaryRepositoryException("Failed to get dictionary health status", e);
        }
    }
    
    /**
     * Check if a dictionary type exists in the system.
     * This is a lightweight operation for existence checking without loading full data.
     * 
     * @param typeCode the dictionary type code to check, must not be null or empty
     * @return true if the dictionary type exists, false otherwise
     * @throws IllegalArgumentException if typeCode is null or empty
     * @throws DictionaryRepositoryException if data access fails
     */
    @Override
    public boolean existsByTypeCode(String typeCode) {
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Type code cannot be null or empty");
        }
        
        log.debug("Repository: Checking if dictionary type exists: typeCode={}", typeCode);
        
        try {
            boolean exists = typeMapper.selectByTypeCode(typeCode).isPresent();
            log.debug("Repository: Dictionary type existence check result: typeCode={}, exists={}", typeCode, exists);
            return exists;
            
        } catch (Exception e) {
            log.error("Repository: Error checking dictionary type existence: typeCode={}, error={}", 
                     typeCode, e.getMessage(), e);
            throw new DictionaryRepositoryException("Failed to check dictionary type existence", e);
        }
    }
    
    /**
     * Check if a dictionary item exists for the given type and item codes.
     * This is a lightweight operation for existence checking without loading full data.
     * 
     * @param typeCode the dictionary type code, must not be null or empty
     * @param itemCode the dictionary item code, must not be null or empty
     * @return true if the dictionary item exists and is not deleted, false otherwise
     * @throws IllegalArgumentException if typeCode or itemCode is null or empty
     * @throws DictionaryRepositoryException if data access fails
     */
    @Override
    public boolean existsByTypeAndItemCode(String typeCode, String itemCode) {
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Type code cannot be null or empty");
        }
        if (itemCode == null || itemCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Item code cannot be null or empty");
        }
        
        log.debug("Repository: Checking if dictionary item exists: typeCode={}, itemCode={}", typeCode, itemCode);
        
        try {
            boolean exists = itemMapper.selectByTypeAndItemCode(typeCode, itemCode).isPresent();
            log.debug("Repository: Dictionary item existence check result: typeCode={}, itemCode={}, exists={}", 
                     typeCode, itemCode, exists);
            return exists;
            
        } catch (Exception e) {
            log.error("Repository: Error checking dictionary item existence: typeCode={}, itemCode={}, error={}", 
                     typeCode, itemCode, e.getMessage(), e);
            throw new DictionaryRepositoryException("Failed to check dictionary item existence", e);
        }
    }
    
    /**
     * Get the count of enabled items for a specific dictionary type.
     * This is an optimized count query without loading the actual items.
     * 
     * @param typeCode the dictionary type code, must not be null or empty
     * @return count of enabled items for the specified type, 0 if type doesn't exist
     * @throws IllegalArgumentException if typeCode is null or empty
     * @throws DictionaryRepositoryException if data access fails
     */
    @Override
    public int countEnabledItemsByType(String typeCode) {
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Type code cannot be null or empty");
        }
        
        log.debug("Repository: Counting enabled dictionary items by type: typeCode={}", typeCode);
        
        try {
            int count = itemMapper.countEnabledByTypeCode(typeCode);
            log.debug("Repository: Enabled item count result: typeCode={}, count={}", typeCode, count);
            return count;
            
        } catch (Exception e) {
            log.error("Repository: Error counting enabled dictionary items: typeCode={}, error={}", 
                     typeCode, e.getMessage(), e);
            throw new DictionaryRepositoryException("Failed to count enabled dictionary items for type: " + typeCode, e);
        }
    }
    
    /**
     * Find dictionary types that have no default items.
     * This is used for health monitoring to identify potential configuration issues.
     * 
     * @return List of type codes that do not have any default items, never null
     * @throws DictionaryRepositoryException if data access fails
     */
    @Override
    public List<String> findTypesWithoutDefaults() {
        log.debug("Repository: Finding dictionary types without default items");
        
        try {
            List<String> typeCodes = itemMapper.selectTypesWithoutDefaults();
            log.debug("Repository: Found {} dictionary types without default items", typeCodes.size());
            
            if (!typeCodes.isEmpty()) {
                log.warn("Repository: Dictionary types without default items detected: {}", typeCodes);
            }
            
            return typeCodes;
            
        } catch (Exception e) {
            log.error("Repository: Error finding dictionary types without defaults: error={}", e.getMessage(), e);
            throw new DictionaryRepositoryException("Failed to find dictionary types without defaults", e);
        }
    }
    
    /**
     * Find dictionary types that have multiple default items.
     * This is used for health monitoring to identify data integrity issues.
     * 
     * @return List of type codes that have more than one default item, never null
     * @throws DictionaryRepositoryException if data access fails
     */
    @Override
    public List<String> findTypesWithMultipleDefaults() {
        log.debug("Repository: Finding dictionary types with multiple default items");
        
        try {
            List<String> typeCodes = itemMapper.selectTypesWithMultipleDefaults();
            log.debug("Repository: Found {} dictionary types with multiple default items", typeCodes.size());
            
            if (!typeCodes.isEmpty()) {
                log.warn("Repository: Dictionary types with multiple default items detected: {}", typeCodes);
            }
            
            return typeCodes;
            
        } catch (Exception e) {
            log.error("Repository: Error finding dictionary types with multiple defaults: error={}", e.getMessage(), e);
            throw new DictionaryRepositoryException("Failed to find dictionary types with multiple defaults", e);
        }
    }
}
