package com.patra.registry.app.service;

import com.patra.registry.app.mapping.DictionaryValidationConverter;
import com.patra.registry.app.util.DictionaryErrorHandler;
import com.patra.registry.contract.query.view.DictionaryHealthQuery;
import com.patra.registry.contract.query.view.DictionaryValidationQuery;
import com.patra.registry.domain.exception.RegistryException;
import com.patra.registry.domain.exception.DictionaryRepositoryException;
import com.patra.registry.domain.model.vo.DictionaryHealthStatus;
import com.patra.registry.domain.model.vo.DictionaryItem;
import com.patra.registry.domain.model.vo.DictionaryReference;
import com.patra.registry.domain.model.vo.ValidationResult;
import com.patra.registry.domain.port.DictionaryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Dictionary validation application service for CQRS read-side validation operations.
 * Provides validation capabilities using contract Query objects for consistency.
 * This service is strictly read-only and does not modify any data.
 * 
 * All validation operations follow CQRS query patterns, providing read-side validation
 * without any command or data modification capabilities.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Service
public class DictionaryValidationAppService {
    
    /** Dictionary repository for data access */
    private final DictionaryRepository dictionaryRepository;
    
    /** Converter for domain to Query object mapping */
    private final DictionaryValidationConverter dictionaryValidationConverter;

    /** Error handler providing consistent exception management */
    private final DictionaryErrorHandler dictionaryErrorHandler;
    
    /**
     * Constructs a new DictionaryValidationAppService with required dependencies.
     * 
     * @param dictionaryRepository the repository for dictionary data access
     * @param dictionaryValidationConverter the converter for domain to query object mapping
     */
    public DictionaryValidationAppService(
            DictionaryRepository dictionaryRepository,
            DictionaryValidationConverter dictionaryValidationConverter,
            DictionaryErrorHandler dictionaryErrorHandler) {
        this.dictionaryRepository = dictionaryRepository;
        this.dictionaryValidationConverter = dictionaryValidationConverter;
        this.dictionaryErrorHandler = dictionaryErrorHandler;
    }
    
    /**
     * Validate a single dictionary reference.
     * Checks if the specified type and item codes exist, are enabled, and not deleted.
     * Provides detailed validation results with specific error messages for different failure scenarios.
     * 
     * @param typeCode the dictionary type code to validate, must not be null or empty
     * @param itemCode the dictionary item code to validate, must not be null or empty
     * @return DictionaryValidationQuery containing validation outcome and error message if invalid
     * @throws IllegalArgumentException if typeCode or itemCode is null or empty
     */
    public DictionaryValidationQuery validateReference(String typeCode, String itemCode) {
        log.debug("Validating dictionary reference: typeCode={}, itemCode={}", typeCode, itemCode);
        dictionaryErrorHandler.validateTypeCode(typeCode);
        dictionaryErrorHandler.validateItemCode(itemCode);

        try {
            return dictionaryErrorHandler.executeWithErrorHandling(() -> {
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

                log.debug("Dictionary validation successful: typeCode={}, itemCode={}", typeCode, itemCode);
                ValidationResult result = ValidationResult.success();
                return dictionaryValidationConverter.toQuery(result, typeCode, itemCode);

            }, "validateReference", typeCode, itemCode);

        } catch (DictionaryRepositoryException e) {
            log.error("Dictionary validation failed due to repository error: typeCode={}, itemCode={}, message={}",
                    typeCode, itemCode, e.getMessage());
            ValidationResult result = ValidationResult.failure("Validation failed due to repository error: " + e.getMessage());
            return dictionaryValidationConverter.toQuery(result, typeCode, itemCode);

        } catch (RegistryException e) {
            log.error("Dictionary validation failed unexpectedly: typeCode={}, itemCode={}, message={}",
                    typeCode, itemCode, e.getMessage());
            ValidationResult result = ValidationResult.failure("Validation failed due to system error: " + e.getMessage());
            return dictionaryValidationConverter.toQuery(result, typeCode, itemCode);
        }
    }
    
    /**
     * Validate multiple dictionary references in batch.
     * Performs validation for each reference and returns corresponding results.
     * Provides efficient batch processing while maintaining individual validation details.
     * 
     * @param references list of dictionary references to validate, must not be null
     * @return List of DictionaryValidationQuery objects corresponding to each input reference, never null
     * @throws IllegalArgumentException if references list is null
     */
    public List<DictionaryValidationQuery> validateReferences(List<DictionaryReference> references) {
        log.debug("Validating {} dictionary references in batch", references != null ? references.size() : 0);
        dictionaryErrorHandler.validateListParameter(references, "References list");

        if (references.isEmpty()) {
            log.debug("Empty references list provided for batch validation");
            return List.of();
        }

        try {
            return dictionaryErrorHandler.executeWithErrorHandling(() -> {
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

            }, "validateReferences");

        } catch (DictionaryRepositoryException e) {
            log.error("Batch validation failed due to repository error: referencesCount={}, message={}",
                    references.size(), e.getMessage());
            String message = "Validation failed due to repository error: " + e.getMessage();
            return references.stream()
                    .map(ref -> dictionaryValidationConverter.toQuery(ValidationResult.failure(message),
                            ref.typeCode(), ref.itemCode()))
                    .toList();

        } catch (RegistryException e) {
            log.error("Batch validation failed unexpectedly: referencesCount={}, message={}",
                    references.size(), e.getMessage());
            String message = "Validation failed due to system error: " + e.getMessage();
            return references.stream()
                    .map(ref -> dictionaryValidationConverter.toQuery(ValidationResult.failure(message),
                            ref.typeCode(), ref.itemCode()))
                    .toList();
        }
    }
    
    /**
     * Get dictionary system health status for monitoring and diagnostics.
     * Provides comprehensive health metrics including integrity issues and system statistics.
     * This operation may be expensive as it aggregates data across all dictionary tables.
     * 
     * @return DictionaryHealthQuery containing system health metrics and issues
     */
    public DictionaryHealthQuery getHealthStatus() {
        log.debug("Getting dictionary system health status");

        return dictionaryErrorHandler.executeWithErrorHandling(() -> {
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

        }, "getHealthStatus");
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
        
        if (reference == null) {
            throw new IllegalArgumentException("Dictionary reference cannot be null");
        }
        
        return validateReference(reference.typeCode(), reference.itemCode());
    }
    
    /**
     * Check if a dictionary reference is valid without returning detailed validation information.
     * Provides a lightweight validation check that returns only a boolean result.
     * 
     * @param typeCode the dictionary type code to validate, must not be null or empty
     * @param itemCode the dictionary item code to validate, must not be null or empty
     * @return true if the reference is valid (exists, enabled, not deleted), false otherwise
     * @throws IllegalArgumentException if typeCode or itemCode is null or empty
     */
    public boolean isValidReference(String typeCode, String itemCode) {
        log.debug("Checking if dictionary reference is valid: typeCode={}, itemCode={}", typeCode, itemCode);
        
        try {
            DictionaryValidationQuery result = validateReference(typeCode, itemCode);
            boolean isValid = result.isValid();
            
            log.debug("Dictionary reference validity check result: typeCode={}, itemCode={}, isValid={}", 
                     typeCode, itemCode, isValid);
            
            return isValid;
            
        } catch (Exception e) {
            log.error("Dictionary reference validity check failed: typeCode={}, itemCode={}, error={}", 
                     typeCode, itemCode, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Get validation statistics for monitoring purposes.
     * Provides summary information about validation operations without performing actual validations.
     * 
     * @return a summary string containing validation-related statistics
     */
    public String getValidationStatistics() {
        log.debug("Getting dictionary validation statistics");
        
        try {
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
            
        } catch (Exception e) {
            log.error("Failed to get validation statistics: error={}", e.getMessage(), e);
            return "Validation statistics unavailable due to error: " + e.getMessage();
        }
    }
}
