package com.patra.registry.app.service;

import com.patra.registry.app.mapping.DictionaryValidationConverter;
import com.patra.registry.domain.model.read.dictionary.DictionaryHealthQuery;
import com.patra.registry.domain.model.read.dictionary.DictionaryValidationQuery;
import com.patra.registry.domain.model.vo.dictionary.DictionaryHealthStatus;
import com.patra.registry.domain.model.vo.dictionary.DictionaryItem;
import com.patra.registry.domain.model.vo.dictionary.DictionaryReference;
import com.patra.registry.domain.model.vo.dictionary.ValidationResult;
import com.patra.registry.domain.port.DictionaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 字典校验应用服务（CQRS 查询侧）。
 *
 * <p>基于契约层 Query 对象提供一致的校验能力；严格只读，不修改数据。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictionaryValidationAppService {

    /** Dictionary repository for data access */
    private final DictionaryRepository dictionaryRepository;

    /** Converter for domain to Query object mapping */
    private final DictionaryValidationConverter dictionaryValidationConverter;

    /** 校验单个字典引用（存在、启用、未删除），返回详细结果。 */
    public DictionaryValidationQuery validateReference(String typeCode, String itemCode) {
        log.debug("Validating dictionary reference: typeCode={}, itemCode={}", typeCode, itemCode);
        requireTypeCode(typeCode);
        requireItemCode(itemCode);

        if (!dictionaryRepository.existsByTypeCode(typeCode)) {
            log.warn("Dictionary validation failed - type not found: typeCode={}, itemCode={}", typeCode, itemCode);
            ValidationResult result = ValidationResult.failure("Dictionary type not found: " + typeCode);
            return dictionaryValidationConverter.toQuery(result, typeCode, itemCode);
        }

        Optional<DictionaryItem> item = dictionaryRepository.findItemByTypeAndCode(typeCode, itemCode);
        if (item.isEmpty()) {
            log.warn("Dictionary validation failed - item not found: typeCode={}, itemCode={}", typeCode, itemCode);
            ValidationResult result = ValidationResult.notFound(typeCode, itemCode);
            return dictionaryValidationConverter.toQuery(result, typeCode, itemCode);
        }

        DictionaryItem dictionaryItem = item.get();

        if (dictionaryItem.deleted()) {
            log.warn("Dictionary validation failed - item deleted: typeCode={}, itemCode={}", typeCode, itemCode);
            ValidationResult result = ValidationResult.deleted(typeCode, itemCode);
            return dictionaryValidationConverter.toQuery(result, typeCode, itemCode);
        }

        if (!dictionaryItem.enabled()) {
            log.warn("Dictionary validation failed - item disabled: typeCode={}, itemCode={}", typeCode, itemCode);
            ValidationResult result = ValidationResult.disabled(typeCode, itemCode);
            return dictionaryValidationConverter.toQuery(result, typeCode, itemCode);
        }

        log.debug("Dictionary validation succeeded: typeCode={}, itemCode={}", typeCode, itemCode);
        ValidationResult result = ValidationResult.success();
        return dictionaryValidationConverter.toQuery(result, typeCode, itemCode);
    }

    /** 批量校验字典引用（存在且启用），返回与入参对应的结果列表。 */
    public List<DictionaryValidationQuery> validateReferences(List<DictionaryReference> references) {
        int size = references == null ? 0 : references.size();
        log.debug("Validating {} dictionary references in batch", size);
        requireReferences(references);

        if (references == null || references.isEmpty()) {
            log.debug("Empty references list provided for batch validation");
            return List.of();
        }

        List<DictionaryValidationQuery> results = references.stream()
                .map(ref -> validateReference(ref.typeCode(), ref.itemCode()))
                .toList();

        long validCount = results.stream().filter(DictionaryValidationQuery::isValid).count();
        long invalidCount = results.size() - validCount;

        log.info("Batch validation completed: total={}, valid={}, invalid={}",
                results.size(), validCount, invalidCount);

        if (invalidCount > 0) {
            log.warn("Batch validation found {} invalid references out of {} total", invalidCount, results.size());
        }

        return results;
    }

    /** 获取字典系统健康状态（聚合统计，可能较重）。 */
    public DictionaryHealthQuery getHealthStatus() {
        log.debug("Getting dictionary system health status");

        DictionaryHealthStatus healthStatus = dictionaryRepository.getHealthStatus();

        if (healthStatus.isHealthy()) {
            log.info("Dictionary system health check: HEALTHY - {} types, {} items ({} enabled)",
                    healthStatus.totalTypes(), healthStatus.totalItems(), healthStatus.enabledItems());
        } else {
            log.warn("Dictionary system health check: ISSUES DETECTED - {} types with problems",
                    healthStatus.getTypesWithIssuesCount());

            if (healthStatus.hasTypesWithoutDefaults()) {
                log.warn("Types without default items: {}", healthStatus.typesWithoutDefault());
            }

            if (healthStatus.hasTypesWithMultipleDefaults()) {
                log.warn("Types with multiple default items: {}", healthStatus.typesWithMultipleDefaults());
            }
        }

        DictionaryHealthQuery result = dictionaryValidationConverter.toQuery(healthStatus);
        log.debug("Successfully retrieved dictionary health status");
        return result;
    }

    /**
     * Validate a dictionary reference using a DictionaryReference object.
     * Convenience method that extracts type and item codes from the reference object.
     *
     * @param reference the dictionary reference to validate, must not be null
     * @return DictionaryValidationQuery containing validation outcome and error message if invalid
     * @throws IllegalArgumentException if reference is null
     */
    public DictionaryValidationQuery validateReference(DictionaryReference reference) {
        log.debug("Validating dictionary reference object: {}", reference != null ? reference.toReferenceString() : "null");
        requireReference(reference);
    final DictionaryReference ref = java.util.Objects.requireNonNull(reference, "reference");
    return validateReference(ref.typeCode(), ref.itemCode());
    }

    /** 轻量校验是否有效（仅返回布尔）。 */
    public boolean isValidReference(String typeCode, String itemCode) {
        log.debug("Checking if dictionary reference is valid: typeCode={}, itemCode={}", typeCode, itemCode);
        DictionaryValidationQuery result = validateReference(typeCode, itemCode);
        boolean isValid = result.isValid();

        log.debug("Dictionary reference validity check result: typeCode={}, itemCode={}, isValid={}",
                typeCode, itemCode, isValid);

        return isValid;
    }

    /** 获取校验统计摘要（监控用）。 */
    public String getValidationStatistics() {
        log.debug("Getting dictionary validation statistics");

        DictionaryHealthQuery health = getHealthStatus();

        String statistics = String.format(
                "Dictionary Validation Statistics: " +
                        "Total Types: %d, Total Items: %d, Enabled Items: %d (%.1f%%), " +
                        "Types without defaults: %d, Types with multiple defaults: %d",
                health.totalTypes(),
                health.totalItems(),
                health.enabledItems(),
                health.getEnabledItemsPercentage(),
                health.typesWithoutDefault().size(),
                health.typesWithMultipleDefaults().size()
        );

        log.debug("Generated validation statistics summary");
        return statistics;
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

    private void requireReferences(List<DictionaryReference> references) {
        if (references == null) {
            throw new IllegalArgumentException("References list cannot be null");
        }
    }

    private void requireReference(DictionaryReference reference) {
        if (reference == null) {
            throw new IllegalArgumentException("Dictionary reference cannot be null");
        }
    }
}
