package com.patra.registry.adapter.rest._internal.client;

import com.patra.registry.adapter.rest._internal.convertor.DictionaryApiConvertor;
import com.patra.registry.api.rpc.dto.dict.DictionaryHealthResp;
import com.patra.registry.api.rpc.dto.dict.DictionaryItemResp;
import com.patra.registry.api.rpc.dto.dict.DictionaryReferenceReq;
import com.patra.registry.api.rpc.dto.dict.DictionaryTypeResp;
import com.patra.registry.api.rpc.dto.dict.DictionaryValidationResp;
import com.patra.registry.api.rpc.client.DictionaryClient;
import com.patra.registry.app.service.DictionaryQueryAppService;
import com.patra.registry.app.service.DictionaryValidationAppService;
import com.patra.registry.contract.query.view.DictionaryHealthQuery;
import com.patra.registry.contract.query.view.DictionaryItemQuery;
import com.patra.registry.contract.query.view.DictionaryTypeQuery;
import com.patra.registry.contract.query.view.DictionaryValidationQuery;
import com.patra.registry.domain.model.vo.DictionaryReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * Dictionary API implementation for internal subsystem access.
 * Implements the Feign client contract and delegates to application services while converting
 * contract query objects into API DTOs for downstream subsystems.
 *
 * <p>This controller provides internal API endpoints following the /_internal/dictionaries/** pattern
 * for consumption by other microservices via Feign clients. All operations are strictly read-only
 * following CQRS query patterns.</p>
 *
 * <p>The controller handles null returns appropriately for 404 scenarios, allowing Feign clients
 * to handle missing resources gracefully. All operations include structured logging with request
 * parameters for monitoring and troubleshooting.</p>
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>Delegation to application services with dedicated MapStruct conversion</li>
 *   <li>Consistent error handling and logging patterns</li>
 *   <li>Optimized for service-to-service communication</li>
 *   <li>Full compliance with DictionaryHttpApi contract</li>
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@RestController
public class DictionaryClientImpl implements DictionaryClient {

    /**
     * Dictionary query application service for read operations
     */
    private final DictionaryQueryAppService dictionaryQueryAppService;

    /**
     * Dictionary validation application service for validation operations
     */
    private final DictionaryValidationAppService dictionaryValidationAppService;

    /**
     * API converter bridging contract query objects and HTTP DTOs
     */
    private final DictionaryApiConvertor dictionaryApiConvertor;

    /**
     * Constructs a new DictionaryApiImpl with required application services.
     *
     * @param dictionaryQueryAppService      the service for dictionary query operations
     * @param dictionaryValidationAppService the service for dictionary validation operations
     * @param dictionaryApiConvertor         the converter bridging contract queries and API DTOs
     */
    public DictionaryClientImpl(
            DictionaryQueryAppService dictionaryQueryAppService,
            DictionaryValidationAppService dictionaryValidationAppService,
            DictionaryApiConvertor dictionaryApiConvertor) {
        this.dictionaryQueryAppService = dictionaryQueryAppService;
        this.dictionaryValidationAppService = dictionaryValidationAppService;
        this.dictionaryApiConvertor = dictionaryApiConvertor;
    }

    /**
     * Get dictionary item by type and item code.
     * Retrieves a specific dictionary item identified by its type code and item code.
     * Returns null for missing or disabled items to enable proper 404 handling by Feign clients.
     *
     * @param typeCode the dictionary type code, must not be null or empty
     * @param itemCode the dictionary item code, must not be null or empty
     * @return DictionaryItemResp object if found and enabled, null for 404 handling by Feign
     * @throws IllegalArgumentException if typeCode or itemCode is null or empty
     */
    @Override
    public DictionaryItemResp getItemByTypeAndCode(String typeCode, String itemCode) {
        log.info("API: Getting dictionary item: typeCode={}, itemCode={}", typeCode, itemCode);

        try {
            Optional<DictionaryItemQuery> result = dictionaryQueryAppService.findItemByTypeAndCode(typeCode, itemCode);

            if (result.isEmpty()) {
                log.info("API: Dictionary item not found, returning null for 404: typeCode={}, itemCode={}",
                        typeCode, itemCode);
                return null; // Return null for 404 handling by Feign
            }

            log.info("API: Successfully returned dictionary item: typeCode={}, itemCode={}", typeCode, itemCode);
            return dictionaryApiConvertor.toItemResp(result.get());

        } catch (IllegalArgumentException e) {
            log.warn("API: Invalid parameters for dictionary item lookup: typeCode={}, itemCode={}, error={}",
                    typeCode, itemCode, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("API: Failed to get dictionary item: typeCode={}, itemCode={}, error={}",
                    typeCode, itemCode, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Get all enabled dictionary items for a specific type.
     * Retrieves all enabled and non-deleted items for the specified dictionary type.
     * Returns empty list if type not found or no enabled items exist.
     *
     * @param typeCode the dictionary type code, must not be null or empty
     * @return List of enabled dictionary item responses, empty list if type not found or no enabled items
     * @throws IllegalArgumentException if typeCode is null or empty
     */
    @Override
    public List<DictionaryItemResp> getEnabledItemsByType(String typeCode) {
        log.info("API: Getting enabled dictionary items for type: typeCode={}", typeCode);

        try {
            List<DictionaryItemQuery> result = dictionaryQueryAppService.findEnabledItemsByType(typeCode);

            log.info("API: Successfully returned {} enabled dictionary items for type: typeCode={}",
                    result.size(), typeCode);
            return dictionaryApiConvertor.toItemResp(result);

        } catch (IllegalArgumentException e) {
            log.warn("API: Invalid type code for enabled items lookup: typeCode={}, error={}",
                    typeCode, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("API: Failed to get enabled dictionary items: typeCode={}, error={}",
                    typeCode, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Get the default dictionary item for a specific type.
     * Retrieves the item marked as default that is enabled and not deleted.
     * Returns null if no default exists or default item is disabled.
     *
     * @param typeCode the dictionary type code, must not be null or empty
     * @return DictionaryItemResp object if default exists and is enabled, null otherwise
     * @throws IllegalArgumentException if typeCode is null or empty
     */
    @Override
    public DictionaryItemResp getDefaultItemByType(String typeCode) {
        log.info("API: Getting default dictionary item for type: typeCode={}", typeCode);

        try {
            Optional<DictionaryItemQuery> result = dictionaryQueryAppService.findDefaultItemByType(typeCode);

            if (result.isEmpty()) {
                log.info("API: No default dictionary item found for type: typeCode={}", typeCode);
                return null; // Return null for consistent handling
            }

            log.info("API: Successfully returned default dictionary item for type: typeCode={}, itemCode={}",
                    typeCode, result.get().itemCode());
            return dictionaryApiConvertor.toItemResp(result.get());

        } catch (IllegalArgumentException e) {
            log.warn("API: Invalid type code for default item lookup: typeCode={}, error={}",
                    typeCode, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("API: Failed to get default dictionary item: typeCode={}, error={}",
                    typeCode, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Validate multiple dictionary references in batch.
     * Validates a list of dictionary references to ensure they exist and are enabled.
     * This is the primary validation endpoint for subsystems to verify dictionary references.
     *
     * @param references list of dictionary reference requests to validate, must not be null
     * @return List of validation results corresponding to each input reference in the same order
     * @throws IllegalArgumentException if references list is null
     */
    @Override
    public List<DictionaryValidationResp> validateReferences(List<DictionaryReferenceReq> references) {
        int requestSize = references != null ? references.size() : 0;
        log.info("API: Validating {} dictionary references in batch", requestSize);

        try {
            List<DictionaryReference> domainReferences = dictionaryApiConvertor.toReference(references);
            List<DictionaryValidationQuery> result = dictionaryValidationAppService.validateReferences(domainReferences);

            long validCount = result.stream().filter(DictionaryValidationQuery::isValid).count();
            long invalidCount = result.size() - validCount;

            log.info("API: Batch validation completed: total={}, valid={}, invalid={}",
                    result.size(), validCount, invalidCount);

            if (invalidCount > 0) {
                log.warn("API: Batch validation found {} invalid references", invalidCount);
            }

            return dictionaryApiConvertor.toValidationResp(result);

        } catch (IllegalArgumentException e) {
            log.warn("API: Invalid references list for batch validation: error={}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("API: Failed to validate dictionary references: referencesCount={}, error={}",
                    requestSize, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Get dictionary item by external system alias.
     * Resolves an external system's code to the corresponding internal dictionary item.
     * Returns null if alias mapping doesn't exist or mapped item is disabled.
     *
     * @param sourceSystem the external system identifier, must not be null or empty
     * @param externalCode the external system's code, must not be null or empty
     * @return DictionaryItemResp object if alias mapping exists and item is enabled, null otherwise
     * @throws IllegalArgumentException if sourceSystem or externalCode is null or empty
     */
    @Override
    public DictionaryItemResp getItemByAlias(String sourceSystem, String externalCode) {
        log.info("API: Getting dictionary item by alias: sourceSystem={}, externalCode={}",
                sourceSystem, externalCode);

        try {
            Optional<DictionaryItemQuery> result = dictionaryQueryAppService.findByAlias(sourceSystem, externalCode);

            if (result.isEmpty()) {
                log.info("API: Dictionary item not found by alias: sourceSystem={}, externalCode={}",
                        sourceSystem, externalCode);
                return null; // Return null for consistent handling
            }

            log.info("API: Successfully returned dictionary item by alias: sourceSystem={}, externalCode={}, itemCode={}",
                    sourceSystem, externalCode, result.get().itemCode());
            return dictionaryApiConvertor.toItemResp(result.get());

        } catch (IllegalArgumentException e) {
            log.warn("API: Invalid parameters for alias lookup: sourceSystem={}, externalCode={}, error={}",
                    sourceSystem, externalCode, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("API: Failed to get dictionary item by alias: sourceSystem={}, externalCode={}, error={}",
                    sourceSystem, externalCode, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Get all dictionary types in the system.
     * Retrieves metadata for all dictionary types including item counts and default status.
     * Returns empty list if no dictionary types exist in the system.
     *
     * @return List of all dictionary type responses ordered by type_code, empty list if no types exist
     */
    @Override
    public List<DictionaryTypeResp> getAllTypes() {
        log.info("API: Getting all dictionary types");

        try {
            List<DictionaryTypeQuery> result = dictionaryQueryAppService.findAllTypes();

            log.info("API: Successfully returned {} dictionary types", result.size());
            return dictionaryApiConvertor.toTypeResp(result);

        } catch (Exception e) {
            log.error("API: Failed to get all dictionary types: error={}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Get dictionary system health status for monitoring.
     * Provides comprehensive health metrics including item counts, integrity issues,
     * and configuration problems for monitoring systems and health check endpoints.
     *
     * @return DictionaryHealthResp containing system health metrics and issue details
     */
    @Override
    public DictionaryHealthResp getHealthStatus() {
        log.info("API: Getting dictionary system health status");

        try {
            DictionaryHealthQuery result = dictionaryValidationAppService.getHealthStatus();

            // Log health status summary
            if (result.isHealthy()) {
                log.info("API: Dictionary system health check: HEALTHY - {} types, {} items ({} enabled)",
                        result.totalTypes(), result.totalItems(), result.enabledItems());
            } else {
                log.warn("API: Dictionary system health check: ISSUES DETECTED - {} types without defaults, {} types with multiple defaults",
                        result.typesWithoutDefault().size(), result.typesWithMultipleDefaults().size());
            }

            return dictionaryApiConvertor.toHealthResp(result);

        } catch (Exception e) {
            log.error("API: Failed to get dictionary health status: error={}", e.getMessage(), e);
            throw e;
        }
    }
}
