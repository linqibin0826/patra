package com.patra.registry.app.service;

import com.patra.registry.app.converter.DictionaryQueryConverter;
import com.patra.registry.domain.model.read.dictionary.DictionaryItemQuery;
import com.patra.registry.domain.model.read.dictionary.DictionaryTypeQuery;
import com.patra.registry.domain.model.vo.dictionary.DictionaryItem;
import com.patra.registry.domain.model.vo.dictionary.DictionaryType;
import com.patra.registry.domain.port.DictionaryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Dictionary query application service for CQRS read operations.
 * Orchestrates dictionary query use cases and uses contract Query objects for consistency.
 * This service is strictly read-only and does not support any command operations.
 * <p>
 * All methods in this service follow CQRS query patterns, providing optimized read access
 * to dictionary data while maintaining clean separation from command operations.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Service
public class DictionaryQueryAppService {

    /**
     * Dictionary repository for data access
     */
    private final DictionaryRepository dictionaryRepository;

    /**
     * Converter for domain to Query object mapping
     */
    private final DictionaryQueryConverter dictionaryQueryConverter;

    /**
     * Constructs a new DictionaryQueryAppService with required dependencies.
     *
     * @param dictionaryRepository     the repository for dictionary data access
     * @param dictionaryQueryConverter the converter for domain to query object mapping
     */
    public DictionaryQueryAppService(
            DictionaryRepository dictionaryRepository,
            DictionaryQueryConverter dictionaryQueryConverter) {
        this.dictionaryRepository = dictionaryRepository;
        this.dictionaryQueryConverter = dictionaryQueryConverter;
    }

    /**
     * 按类型与项编码查询单个字典项（不加载聚合）。
     * 项不存在/禁用/删除则返回空。
     */
    public Optional<DictionaryItemQuery> findItemByTypeAndCode(String typeCode, String itemCode) {
        log.debug("Finding dictionary item: typeCode={}, itemCode={}", typeCode, itemCode);
        requireTypeCode(typeCode);
        requireItemCode(itemCode);

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
    }

    /**
     * 查询某类型下所有启用项（排序：sort_order 升序，其次 item_code 升序）。
     */
    public List<DictionaryItemQuery> findEnabledItemsByType(String typeCode) {
        log.debug("Finding enabled dictionary items for type: typeCode={}", typeCode);
        requireTypeCode(typeCode);

        List<DictionaryItem> domainItems = dictionaryRepository.findEnabledItemsByType(typeCode);

        List<DictionaryItemQuery> result = domainItems.stream()
                .map(item -> dictionaryQueryConverter.toQuery(item, typeCode))
                .toList();

        log.info("Found {} enabled dictionary items for type: typeCode={}", result.size(), typeCode);
        return result;
    }

    /**
     * 查询某类型的默认项（可用且未删除）。若存在多个默认项将记录告警。
     */
    public Optional<DictionaryItemQuery> findDefaultItemByType(String typeCode) {
        log.debug("Finding default dictionary item for type: typeCode={}", typeCode);
        requireTypeCode(typeCode);

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
    }

    /**
     * 通过外部系统别名查询字典项（仅返回可用且未删除的项）。
     */
    public Optional<DictionaryItemQuery> findByAlias(String sourceSystem, String externalCode) {
        log.debug("Finding dictionary item by alias: sourceSystem={}, externalCode={}", sourceSystem, externalCode);
        requireText(sourceSystem, "Source system cannot be null or empty");
        requireText(externalCode, "External code cannot be null or empty");

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
    }

    /**
     * 查询系统内所有字典类型（含项数与默认项等元数据）。
     */
    public List<DictionaryTypeQuery> findAllTypes() {
        log.debug("Finding all dictionary types");
        List<DictionaryType> domainTypes = dictionaryRepository.findAllTypes();

        List<DictionaryTypeQuery> result = domainTypes.stream()
                .map(this::convertTypeToQuery)
                .toList();

        log.info("Found {} dictionary types in system", result.size());
        return result;
    }

    /**
     * 将领域类型转换为带元数据的查询对象。
     */
    private DictionaryTypeQuery convertTypeToQuery(DictionaryType domainType) {
        int enabledItemCount = dictionaryRepository.countEnabledItemsByType(domainType.typeCode());
        boolean hasDefault = dictionaryRepository.findDefaultItemByType(domainType.typeCode()).isPresent();
        return dictionaryQueryConverter.toQuery(domainType, enabledItemCount, hasDefault);
    }

    private void requireTypeCode(String typeCode) {
        requireText(typeCode, "Dictionary type code cannot be null or empty");
    }

    private void requireItemCode(String itemCode) {
        requireText(itemCode, "Dictionary item code cannot be null or empty");
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
