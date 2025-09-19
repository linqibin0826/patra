package com.patra.registry.api.rpc.endpoint;

import com.patra.registry.api.rpc.dto.dict.DictionaryHealthResp;
import com.patra.registry.api.rpc.dto.dict.DictionaryItemResp;
import com.patra.registry.api.rpc.dto.dict.DictionaryReferenceReq;
import com.patra.registry.api.rpc.dto.dict.DictionaryTypeResp;
import com.patra.registry.api.rpc.dto.dict.DictionaryValidationResp;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Dictionary HTTP API contract for internal subsystem access.
 * Defines the REST endpoints that subsystems can consume via Feign clients.
 * This interface is strictly read-only following CQRS query patterns and provides
 * a clean contract boundary for dictionary operations across microservices.
 * 
 * <p>All endpoints use the internal API path pattern (/_internal/dictionaries/**) 
 * to distinguish from public API endpoints. The interface returns dedicated API DTO objects
 * for consistent data structures between services.</p>
 * 
 * <p>Supported operations include:</p>
 * <ul>
 *   <li>Dictionary item retrieval by type and code</li>
 *   <li>Enabled items listing by type</li>
 *   <li>Default item retrieval by type</li>
 *   <li>Batch validation of dictionary references</li>
 *   <li>External alias resolution</li>
 *   <li>Dictionary type metadata retrieval</li>
 *   <li>System health status monitoring</li>
 * </ul>
 * 
 * @author linqibin
 * @since 0.1.0
 */
public interface DictionaryEndpoint {
    
    /** Base path for internal dictionary API endpoints */
    String BASE_PATH = "/_internal/dictionaries";
    
    /**
     * Get dictionary item by type and item code.
     * Retrieves a specific dictionary item identified by its type code and item code.
     * Only returns enabled and non-deleted items.
     * 
     * @param typeCode the dictionary type code, must not be null or empty
     * @param itemCode the dictionary item code, must not be null or empty
     * @return DictionaryItemResp object if found and enabled, null if not found or disabled
     * @throws IllegalArgumentException if typeCode or itemCode is null or empty
     */
    @GetMapping(BASE_PATH + "/types/{typeCode}/items/{itemCode}")
    DictionaryItemResp getItemByTypeAndCode(@PathVariable("typeCode") String typeCode, 
                                            @PathVariable("itemCode") String itemCode);
    
    /**
     * Get all enabled dictionary items for a specific type.
     * Retrieves all enabled and non-deleted items for the specified dictionary type,
     * sorted by sort_order ascending, then by item_code ascending.
     * 
     * @param typeCode the dictionary type code, must not be null or empty
     * @return List of enabled dictionary items, empty list if type not found or no enabled items
     * @throws IllegalArgumentException if typeCode is null or empty
     */
    @GetMapping(BASE_PATH + "/types/{typeCode}/items")
    List<DictionaryItemResp> getEnabledItemsByType(@PathVariable("typeCode") String typeCode);
    
    /**
     * Get the default dictionary item for a specific type.
     * Retrieves the item marked as default (is_default=true) for the specified type.
     * Only returns enabled and non-deleted default items.
     * 
     * @param typeCode the dictionary type code, must not be null or empty
     * @return DictionaryItemResp object if default exists and is enabled, null if no default or disabled
     * @throws IllegalArgumentException if typeCode is null or empty
     */
    @GetMapping(BASE_PATH + "/types/{typeCode}/default")
    DictionaryItemResp getDefaultItemByType(@PathVariable("typeCode") String typeCode);
    
    /**
     * Validate multiple dictionary references in batch.
     * Validates a list of dictionary references to ensure they exist and are enabled.
     * This is the primary validation endpoint for subsystems to verify dictionary references
     * before persisting business data.
     * 
     * @param references list of dictionary references to validate, must not be null
     * @return List of validation results corresponding to each input reference in the same order
     * @throws IllegalArgumentException if references list is null
     */
    @PostMapping(BASE_PATH + "/validate")
    List<DictionaryValidationResp> validateReferences(@RequestBody List<DictionaryReferenceReq> references);
    
    /**
     * Get dictionary item by external system alias.
     * Resolves an external system's code to the corresponding internal dictionary item.
     * This enables integration with legacy systems and external data sources that use
     * different coding schemes.
     * 
     * @param sourceSystem the external system identifier, must not be null or empty
     * @param externalCode the external system's code, must not be null or empty
     * @return DictionaryItemResp object if alias mapping exists and item is enabled, null otherwise
     * @throws IllegalArgumentException if sourceSystem or externalCode is null or empty
     */
    @GetMapping(BASE_PATH + "/aliases")
    DictionaryItemResp getItemByAlias(@RequestParam("sourceSystem") String sourceSystem,
                                      @RequestParam("externalCode") String externalCode);
    
    /**
     * Get all dictionary types in the system.
     * Retrieves metadata for all dictionary types including item counts and default status.
     * Useful for administrative interfaces and system monitoring.
     * 
     * @return List of all dictionary types ordered by type_code, empty list if no types exist
     */
    @GetMapping(BASE_PATH + "/types")
    List<DictionaryTypeResp> getAllTypes();
    
    /**
     * Get dictionary system health status for monitoring.
     * Provides comprehensive health metrics including item counts, integrity issues,
     * and configuration problems. Used by monitoring systems and health check endpoints.
     * 
     * @return DictionaryHealthResp containing system health metrics and issue details
     */
    @GetMapping(BASE_PATH + "/health")
    DictionaryHealthResp getHealthStatus();
}
