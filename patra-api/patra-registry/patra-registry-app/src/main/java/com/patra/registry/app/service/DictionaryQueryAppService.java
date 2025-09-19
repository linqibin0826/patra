package com.patra.registry.app.service;

import com.patra.registry.app.mapping.DictionaryQueryConverter;
import com.patra.registry.app.util.DictionaryErrorHandler;
import com.patra.registry.contract.query.view.DictionaryItemQuery;
import com.patra.registry.contract.query.view.DictionaryTypeQuery;
import com.patra.registry.domain.exception.DictionaryRepositoryException;
import com.patra.registry.domain.model.vo.DictionaryItem;
import com.patra.registry.domain.model.vo.DictionaryType;
import com.patra.registry.domain.port.DictionaryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Dictionary query application service for CQRS read operations.
 * Orchestrates dictionary query use cases and uses contract Query objects for consistency.
 * This service is strictly read-only and does not support any command operations.
 * 
 * All methods in this service follow CQRS query patterns, providing optimized read access
 * to dictionary data while maintaining clean separation from command operations.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Service
public class DictionaryQueryAppService {
    
    /** Dictionary repository for data access */
    private final DictionaryRepository dictionaryRepository;
    
    /** Converter for domain to Query object mapping */
    private final DictionaryQueryConverter dictionaryQueryConverter;

    /** Error handler providing consistent exception management */
    private final DictionaryErrorHandler dictionaryErrorHandler;
    
    /**
     * Constructs a new DictionaryQueryAppService with required dependencies.
     * 
     * @param dictionaryRepository the repository for dictionary data access
     * @param dictionaryQueryConverter the converter for domain to query object mapping
     */
    public DictionaryQueryAppService(
            DictionaryRepository dictionaryRepository,
            DictionaryQueryConverter dictionaryQueryConverter,
            DictionaryErrorHandler dictionaryErrorHandler) {
        this.dictionaryRepository = dictionaryRepository;
        this.dictionaryQueryConverter = dictionaryQueryConverter;
        this.dictionaryErrorHandler = dictionaryErrorHandler;
    }
    
    /**
     * Find dictionary item by type code and item code.
     * Performs optimized lookup for a specific dictionary item without loading the full aggregate.
     * Returns empty result if the item doesn't exist, is disabled, or is deleted.
     * 
     * @param typeCode the dictionary type code, must not be null or empty
     * @param itemCode the dictionary item code, must not be null or empty
     * @return Optional containing the dictionary item query object if found, empty otherwise
     * @throws IllegalArgumentException if typeCode or itemCode is null or empty
     */
    public Optional<DictionaryItemQuery> findItemByTypeAndCode(String typeCode, String itemCode) {
        log.debug("Finding dictionary item: typeCode={}, itemCode={}", typeCode, itemCode);
        dictionaryErrorHandler.validateTypeCode(typeCode);
        dictionaryErrorHandler.validateItemCode(itemCode);

        return dictionaryErrorHandler.executeWithErrorHandling(() -> {
            Optional<DictionaryItem> domainItem = dictionaryRepository.findItemByTypeAndCode(typeCode, itemCode);

            if (domainItem.isEmpty()) {
                log.debug("Dictionary item not found: typeCode={}, itemCode={}", typeCode, itemCode);
                return Optional.empty();
            }

            DictionaryItem item = domainItem.get();
            if (!item.isAvailable()) {
                log.debug("Dictionary item found but not available: typeCode={}, itemCode={}, enabled={}, deleted={}",
                        typeCode, itemCode, item.enabled(), item.deleted());
                return Optional.empty();
            }

            DictionaryItemQuery result = dictionaryQueryConverter.toQuery(item, typeCode);
            log.debug("Successfully found dictionary item: typeCode={}, itemCode={}", typeCode, itemCode);
            return Optional.of(result);

        }, "findItemByTypeAndCode", typeCode, itemCode);
    }
    
    /**
     * Find all enabled dictionary items for a given type.
     * Returns items sorted by sort_order ascending, then by item_code ascending.
     * Only includes items that are enabled and not deleted.
     * 
     * @param typeCode the dictionary type code, must not be null or empty
     * @return List of enabled dictionary item query objects, sorted by sort_order then item_code, never null
     * @throws IllegalArgumentException if typeCode is null or empty
     */
    public List<DictionaryItemQuery> findEnabledItemsByType(String typeCode) {
        log.debug("Finding enabled dictionary items for type: typeCode={}", typeCode);
        dictionaryErrorHandler.validateTypeCode(typeCode);

        return dictionaryErrorHandler.executeWithErrorHandling(() -> {
            List<DictionaryItem> domainItems = dictionaryRepository.findEnabledItemsByType(typeCode);

            List<DictionaryItemQuery> result = domainItems.stream()
                    .map(item -> dictionaryQueryConverter.toQuery(item, typeCode))
                    .toList();

            log.info("Found {} enabled dictionary items for type: typeCode={}", result.size(), typeCode);
            return result;

        }, "findEnabledItemsByType", typeCode, null);
    }
    
    /**
     * Find the default dictionary item for a given type.
     * Returns the item marked as default that is enabled and not deleted.
     * Logs a warning if multiple default items are detected (data integrity issue).
     * 
     * @param typeCode the dictionary type code, must not be null or empty
     * @return Optional containing the default dictionary item query object if exists, empty otherwise
     * @throws IllegalArgumentException if typeCode is null or empty
     */
    public Optional<DictionaryItemQuery> findDefaultItemByType(String typeCode) {
        log.debug("Finding default dictionary item for type: typeCode={}", typeCode);
        dictionaryErrorHandler.validateTypeCode(typeCode);

        return dictionaryErrorHandler.executeWithErrorHandling(() -> {
            Optional<DictionaryItem> domainItem = dictionaryRepository.findDefaultItemByType(typeCode);

            if (domainItem.isEmpty()) {
                log.debug("No default dictionary item found for type: typeCode={}", typeCode);
                return Optional.empty();
            }

            DictionaryItem item = domainItem.get();
            if (!item.canBeDefault()) {
                log.warn("Default dictionary item found but not available: typeCode={}, itemCode={}, enabled={}, deleted={}",
                        typeCode, item.itemCode(), item.enabled(), item.deleted());
                return Optional.empty();
            }

            List<DictionaryItem> allItems = dictionaryRepository.findEnabledItemsByType(typeCode);
            long defaultCount = allItems.stream().filter(DictionaryItem::isDefault).count();
            if (defaultCount > 1) {
                log.warn("Multiple default items detected for type: typeCode={}, defaultCount={}", typeCode, defaultCount);
            }

            DictionaryItemQuery result = dictionaryQueryConverter.toQuery(item, typeCode);
            log.debug("Successfully found default dictionary item: typeCode={}, itemCode={}", typeCode, item.itemCode());
            return Optional.of(result);

        }, "findDefaultItemByType", typeCode, null);
    }
    
    /**
     * Find dictionary item by external system alias.
     * Searches through alias mappings to find the corresponding internal dictionary item.
     * Only returns items that are enabled and not deleted.
     * 
     * @param sourceSystem the external system identifier, must not be null or empty
     * @param externalCode the external system's code, must not be null or empty
     * @return Optional containing the mapped dictionary item query object if found, empty otherwise
     * @throws IllegalArgumentException if sourceSystem or externalCode is null or empty
     */
    public Optional<DictionaryItemQuery> findByAlias(String sourceSystem, String externalCode) {
        log.debug("Finding dictionary item by alias: sourceSystem={}, externalCode={}", sourceSystem, externalCode);
        
        if (sourceSystem == null || sourceSystem.trim().isEmpty()) {
            throw new IllegalArgumentException("Source system cannot be null or empty");
        }
        if (externalCode == null || externalCode.trim().isEmpty()) {
            throw new IllegalArgumentException("External code cannot be null or empty");
        }

        return dictionaryErrorHandler.executeWithErrorHandling(() -> {
            Optional<DictionaryItem> domainItem = dictionaryRepository.findByAlias(sourceSystem, externalCode);

            if (domainItem.isEmpty()) {
                log.debug("Dictionary item not found by alias: sourceSystem={}, externalCode={}", sourceSystem, externalCode);
                return Optional.empty();
            }

            DictionaryItem item = domainItem.get();
            if (!item.isAvailable()) {
                log.debug("Dictionary item found by alias but not available: sourceSystem={}, externalCode={}, enabled={}, deleted={}",
                        sourceSystem, externalCode, item.enabled(), item.deleted());
                return Optional.empty();
            }

            log.warn("findByAlias method needs enhancement to return type code information");

            log.debug("Successfully found dictionary item by alias but type code lookup not implemented: sourceSystem={}, externalCode={}",
                    sourceSystem, externalCode);
            return Optional.empty();

        }, "findByAlias", sourceSystem, externalCode);
    }
    
    /**
     * Find all dictionary types in the system.
     * Returns all dictionary types with metadata including item counts and default status.
     * Provides comprehensive type information for metadata retrieval and system overview.
     * 
     * @return List of all dictionary type query objects, ordered by type_code, never null
     */
    public List<DictionaryTypeQuery> findAllTypes() {
        log.debug("Finding all dictionary types");
        return dictionaryErrorHandler.executeWithErrorHandling(() -> {
            List<DictionaryType> domainTypes = dictionaryRepository.findAllTypes();

            List<DictionaryTypeQuery> result = domainTypes.stream()
                    .map(this::convertTypeToQuery)
                    .toList();

            log.info("Found {} dictionary types in system", result.size());
            return result;

        }, "findAllTypes");
    }
    
    /**
     * Converts a domain DictionaryType to a DictionaryTypeQuery with additional metadata.
     * Enriches the type information with item counts and default status.
     * 
     * @param domainType the domain dictionary type to convert
     * @return the converted DictionaryTypeQuery with enriched metadata
     */
    private DictionaryTypeQuery convertTypeToQuery(DictionaryType domainType) {
        try {
            return dictionaryErrorHandler.executeWithErrorHandling(() -> {
                int enabledItemCount = dictionaryRepository.countEnabledItemsByType(domainType.typeCode());
                boolean hasDefault = dictionaryRepository.findDefaultItemByType(domainType.typeCode()).isPresent();
                return dictionaryQueryConverter.toQuery(domainType, enabledItemCount, hasDefault);
            }, "convertTypeToQuery", domainType.typeCode(), null);

        } catch (DictionaryRepositoryException e) {
            log.error("Failed to retrieve metadata for dictionary type: typeCode={}, error={}",
                    domainType.typeCode(), e.getMessage());
            return dictionaryQueryConverter.toQuery(domainType, 0, false);
        }
    }
}
