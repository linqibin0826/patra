# Design Document

## Overview

The dictionary read pipeline implements a comprehensive **read-only CQRS system** for accessing system dictionary data in the patra-registry service. This design strictly follows the **Query side of CQRS** - no write operations are included in this pipeline. The design follows hexagonal architecture principles with clear separation between domain logic, application orchestration, infrastructure persistence, and adapter interfaces. The system provides unified access to dictionary types, items, aliases, and validation capabilities while maintaining high performance and data integrity.

### CQRS Design Principles
- **Read-Only Operations**: This pipeline exclusively handles query operations (GET requests)
- **No Command Operations**: Write operations (CREATE, UPDATE, DELETE) are explicitly excluded
- **Query Optimization**: All components are optimized for read performance and data retrieval
- **Eventual Consistency**: Reads from optimized views and cached data structures

## Architecture

### Hexagonal Architecture Layers

```
┌─────────────────────────────────────────────────────────────┐
│                        Adapter Layer                        │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐ │
│  │ REST Controllers │  │   Internal API  │  │   Health    │ │
│  │                 │  │   (Contract)    │  │   Checks    │ │
│  └─────────────────┘  └─────────────────┘  └─────────────┘ │
└─────────────────────────────────────────────────────────────┘
                                │
┌─────────────────────────────────────────────────────────────┐
│                      Application Layer                      │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐ │
│  │ Dict Query      │  │ Dict Validation │  │   Health    │ │
│  │ App Service     │  │ App Service     │  │ App Service │ │
│  └─────────────────┘  └─────────────────┘  └─────────────┘ │
└─────────────────────────────────────────────────────────────┘
                                │
┌─────────────────────────────────────────────────────────────┐
│                        Domain Layer                         │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐ │
│  │ Dictionary      │  │ Dictionary      │  │   Value     │ │
│  │ Aggregate       │  │ Repository Port │  │   Objects   │ │
│  └─────────────────┘  └─────────────────┘  └─────────────┘ │
└─────────────────────────────────────────────────────────────┘
                                │
┌─────────────────────────────────────────────────────────────┐
│                    Infrastructure Layer                     │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐ │
│  │ MyBatis-Plus    │  │ Dictionary      │  │   Entity    │ │
│  │ Mappers         │  │ Repository Impl │  │ Converters  │ │
│  └─────────────────┘  └─────────────────┘  └─────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### Data Flow

1. **Inbound Request** → Adapter Layer (REST Controller or Internal API)
2. **Request Validation** → Application Layer (App Service)
3. **Domain Logic** → Domain Layer (Aggregate + Repository Port)
4. **Data Access** → Infrastructure Layer (Repository Implementation)
5. **Entity Mapping** → Infrastructure Layer (MapStruct Converters)
6. **Response Assembly** → Application Layer (DTO Mapping)
7. **Outbound Response** → Adapter Layer (Response Formatting)

## Components and Interfaces

### Domain Layer Components

#### Dictionary Aggregate
```java
/**
 * Dictionary aggregate root representing dictionary domain concepts for read operations.
 * This aggregate is designed for CQRS query operations only - no command operations are supported.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Data
public class Dictionary {
    
    /** Dictionary type metadata */
    private DictionaryType type;
    
    /** List of dictionary items belonging to this type */
    private List<DictionaryItem> items;
    
    /** List of external system aliases for dictionary items */
    private List<DictionaryAlias> aliases;
    
    /**
     * Find dictionary item by item code within this dictionary type.
     * 
     * @param itemCode the item code to search for
     * @return Optional containing the dictionary item if found, empty otherwise
     */
    public Optional<DictionaryItem> findItemByCode(String itemCode);
    
    /**
     * Find the default dictionary item for this type.
     * 
     * @return Optional containing the default item if exists, empty otherwise
     */
    public Optional<DictionaryItem> findDefaultItem();
    
    /**
     * Get all enabled dictionary items for this type.
     * 
     * @return List of enabled dictionary items, sorted by sort_order and item_code
     */
    public List<DictionaryItem> getEnabledItems();
    
    /**
     * Validate if an item reference is valid for the given type.
     * 
     * @param typeCode the dictionary type code
     * @param itemCode the dictionary item code
     * @return true if the reference is valid, false otherwise
     */
    public boolean validateItemReference(String typeCode, String itemCode);
}
```

#### Value Objects
```java
/**
 * Dictionary type value object representing dictionary type metadata.
 * 
 * @param typeCode unique code identifying the dictionary type
 * @param typeName human-readable name of the dictionary type
 * @param description detailed description of the dictionary type purpose
 * @param allowCustomItems whether this type allows custom items to be added
 * @param isSystem whether this is a system-managed dictionary type
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryType(String typeCode, String typeName, String description, boolean allowCustomItems, boolean isSystem);

/**
 * Dictionary item value object representing individual dictionary entries.
 * 
 * @param itemCode unique code identifying the dictionary item within its type
 * @param displayName human-readable display name for UI presentation
 * @param description detailed description of the dictionary item
 * @param isDefault whether this item is the default for its type
 * @param sortOrder numeric sort order for display ordering
 * @param enabled whether this item is currently enabled for use
 * @param deleted whether this item has been soft-deleted
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryItem(String itemCode, String displayName, String description, boolean isDefault, int sortOrder, boolean enabled, boolean deleted);

/**
 * Dictionary alias value object for external system integration.
 * 
 * @param sourceSystem identifier of the external system providing the alias
 * @param externalCode the external system's code for this dictionary item
 * @param externalLabel the external system's label for this dictionary item
 * @param notes additional notes about the alias mapping
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryAlias(String sourceSystem, String externalCode, String externalLabel, String notes);

/**
 * Dictionary reference value object for validation operations.
 * 
 * @param typeCode the dictionary type code being referenced
 * @param itemCode the dictionary item code being referenced
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryReference(String typeCode, String itemCode);

/**
 * Validation result value object containing validation outcome.
 * 
 * @param isValid whether the validation passed
 * @param errorMessage error message if validation failed, null if valid
 * @author linqibin
 * @since 0.1.0
 */
public record ValidationResult(boolean isValid, String errorMessage);
```

#### Repository Port
```java
/**
 * Domain repository port for dictionary read operations.
 * This interface defines the contract for dictionary data access in the CQRS query model.
 * All operations are read-only - no command operations are supported.
 * 
 * @author linqibin
 * @since 0.1.0
 */
public interface DictionaryRepository {
    
    /**
     * Find dictionary aggregate by type code.
     * 
     * @param typeCode the dictionary type code to search for
     * @return Optional containing the dictionary if found, empty otherwise
     */
    Optional<Dictionary> findByTypeCode(String typeCode);
    
    /**
     * Find all dictionary types in the system.
     * 
     * @return List of all dictionary types, ordered by type_code
     */
    List<DictionaryType> findAllTypes();
    
    /**
     * Find specific dictionary item by type and item code.
     * 
     * @param typeCode the dictionary type code
     * @param itemCode the dictionary item code
     * @return Optional containing the dictionary item if found, empty otherwise
     */
    Optional<DictionaryItem> findItemByTypeAndCode(String typeCode, String itemCode);
    
    /**
     * Find the default dictionary item for a given type.
     * 
     * @param typeCode the dictionary type code
     * @return Optional containing the default item if exists, empty otherwise
     */
    Optional<DictionaryItem> findDefaultItemByType(String typeCode);
    
    /**
     * Find all enabled dictionary items for a given type.
     * 
     * @param typeCode the dictionary type code
     * @return List of enabled items, sorted by sort_order then item_code
     */
    List<DictionaryItem> findEnabledItemsByType(String typeCode);
    
    /**
     * Find dictionary item by external system alias.
     * 
     * @param sourceSystem the external system identifier
     * @param externalCode the external system's code
     * @return Optional containing the mapped dictionary item if found, empty otherwise
     */
    Optional<DictionaryItem> findByAlias(String sourceSystem, String externalCode);
    
    /**
     * Get dictionary system health status for monitoring.
     * 
     * @return DictionaryHealthStatus containing system health metrics
     */
    DictionaryHealthStatus getHealthStatus();
}
```

### Application Layer Components

#### Dictionary Query App Service
```java
/**
 * Dictionary query application service for CQRS read operations.
 * Orchestrates dictionary query use cases and uses contract Query objects for consistency.
 * This service is strictly read-only and does not support any command operations.
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
    
    /**
     * Find dictionary item by type code and item code.
     * 
     * @param typeCode the dictionary type code
     * @param itemCode the dictionary item code
     * @return Optional containing the dictionary item query object if found, empty otherwise
     */
    public Optional<DictionaryItemQuery> findItemByTypeAndCode(String typeCode, String itemCode) {
        log.debug("Finding dictionary item: typeCode={}, itemCode={}", typeCode, itemCode);
        
        Optional<DictionaryItem> domainItem = dictionaryRepository.findItemByTypeAndCode(typeCode, itemCode);
        if (domainItem.isEmpty()) {
            log.debug("Dictionary item not found: typeCode={}, itemCode={}", typeCode, itemCode);
            return Optional.empty();
        }
        
        DictionaryItemQuery result = dictionaryQueryConverter.toQuery(domainItem.get());
        log.debug("Successfully found dictionary item: typeCode={}, itemCode={}", typeCode, itemCode);
        return Optional.of(result);
    }
    
    /**
     * Find all enabled dictionary items for a given type.
     * 
     * @param typeCode the dictionary type code
     * @return List of enabled dictionary item query objects, sorted by sort_order then item_code
     */
    public List<DictionaryItemQuery> findEnabledItemsByType(String typeCode);
    
    /**
     * Find the default dictionary item for a given type.
     * 
     * @param typeCode the dictionary type code
     * @return Optional containing the default dictionary item query object if exists, empty otherwise
     */
    public Optional<DictionaryItemQuery> findDefaultItemByType(String typeCode);
    
    /**
     * Find dictionary item by external system alias.
     * 
     * @param sourceSystem the external system identifier
     * @param externalCode the external system's code
     * @return Optional containing the mapped dictionary item query object if found, empty otherwise
     */
    public Optional<DictionaryItemQuery> findByAlias(String sourceSystem, String externalCode);
    
    /**
     * Find all dictionary types in the system.
     * 
     * @return List of all dictionary type query objects, ordered by type_code
     */
    public List<DictionaryTypeQuery> findAllTypes();
}
```

#### Dictionary Validation App Service
```java
/**
 * Dictionary validation application service for CQRS read-side validation operations.
 * Provides validation capabilities using contract Query objects for consistency.
 * This service is strictly read-only and does not modify any data.
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
    
    /**
     * Validate a single dictionary reference.
     * 
     * @param typeCode the dictionary type code to validate
     * @param itemCode the dictionary item code to validate
     * @return DictionaryValidationQuery containing validation outcome and error message if invalid
     */
    public DictionaryValidationQuery validateReference(String typeCode, String itemCode) {
        log.debug("Validating dictionary reference: typeCode={}, itemCode={}", typeCode, itemCode);
        
        Optional<DictionaryItem> item = dictionaryRepository.findItemByTypeAndCode(typeCode, itemCode);
        if (item.isEmpty()) {
            log.warn("Dictionary validation failed - item not found: typeCode={}, itemCode={}", typeCode, itemCode);
            return new DictionaryValidationQuery(typeCode, itemCode, false, "Dictionary item not found");
        }
        
        if (!item.get().enabled()) {
            log.warn("Dictionary validation failed - item disabled: typeCode={}, itemCode={}", typeCode, itemCode);
            return new DictionaryValidationQuery(typeCode, itemCode, false, "Dictionary item is disabled");
        }
        
        log.debug("Dictionary validation successful: typeCode={}, itemCode={}", typeCode, itemCode);
        return new DictionaryValidationQuery(typeCode, itemCode, true, null);
    }
    
    /**
     * Validate multiple dictionary references in batch.
     * 
     * @param references list of dictionary references to validate
     * @return List of DictionaryValidationQuery objects corresponding to each input reference
     */
    public List<DictionaryValidationQuery> validateReferences(List<DictionaryReference> references);
    
    /**
     * Get dictionary system health status for monitoring and diagnostics.
     * 
     * @return DictionaryHealthQuery containing system health metrics and issues
     */
    public DictionaryHealthQuery getHealthStatus();
}
```

### Infrastructure Layer Components

#### Database Entities
```java
// MyBatis-Plus entities inheriting from BaseDO
@TableName("sys_dict_type")
public class RegSysDictTypeDO extends BaseDO {
    private String typeCode;
    private String typeName;
    private String description;
    private Boolean allowCustomItems;
    private Boolean isSystem;
}

@TableName("sys_dict_item")
public class RegSysDictItemDO extends BaseDO {
    private Long typeId;
    private String itemCode;
    private String displayName;
    private String description;
    private Boolean isDefault;
    private Integer sortOrder;
    private Boolean enabled;
    private Boolean deleted;
}

@TableName("sys_dict_item_alias")
public class RegSysDictItemAliasDO extends BaseDO {
    private Long itemId;
    private String sourceSystem;
    private String externalCode;
    private String externalLabel;
    private String notes;
}
```

#### MyBatis-Plus Mappers
```java
@Mapper
public interface RegSysDictTypeMapper extends BaseMapper<RegSysDictTypeDO> {
    // Custom query methods using MyBatis-Plus or XML
}

@Mapper
public interface RegSysDictItemMapper extends BaseMapper<RegSysDictItemDO> {
    List<RegSysDictItemDO> selectEnabledByTypeCode(@Param("typeCode") String typeCode);
    RegSysDictItemDO selectDefaultByTypeCode(@Param("typeCode") String typeCode);
}

@Mapper
public interface RegSysDictItemAliasMapper extends BaseMapper<RegSysDictItemAliasDO> {
    RegSysDictItemDO selectItemByAlias(@Param("sourceSystem") String sourceSystem, @Param("externalCode") String externalCode);
}
```

#### Repository Implementation
```java
/**
 * Dictionary repository implementation using MyBatis-Plus.
 * Provides data access for dictionary CQRS read operations with optimized queries.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Repository
public class DictionaryRepositoryMpImpl implements DictionaryRepository {
    
    /** MyBatis-Plus mapper for dictionary types */
    private final RegSysDictTypeMapper typeMapper;
    
    /** MyBatis-Plus mapper for dictionary items */
    private final RegSysDictItemMapper itemMapper;
    
    /** MyBatis-Plus mapper for dictionary aliases */
    private final RegSysDictItemAliasMapper aliasMapper;
    
    /** Entity to domain converter */
    private final DictionaryEntityConverter entityConverter;
    
    /**
     * Find dictionary item by type and item code.
     * Uses v_sys_dict_item_enabled view for optimized enabled item queries.
     * 
     * @param typeCode the dictionary type code
     * @param itemCode the dictionary item code
     * @return Optional containing the dictionary item if found, empty otherwise
     */
    @Override
    public Optional<DictionaryItem> findItemByTypeAndCode(String typeCode, String itemCode) {
        log.debug("Repository: Finding dictionary item by type and code: typeCode={}, itemCode={}", typeCode, itemCode);
        
        try {
            RegSysDictItemDO entity = itemMapper.selectItemByTypeAndCode(typeCode, itemCode);
            if (entity == null) {
                log.debug("Repository: Dictionary item not found in database: typeCode={}, itemCode={}", typeCode, itemCode);
                return Optional.empty();
            }
            
            DictionaryItem domainItem = entityConverter.toDomain(entity);
            log.debug("Repository: Successfully converted entity to domain: typeCode={}, itemCode={}", typeCode, itemCode);
            return Optional.of(domainItem);
            
        } catch (Exception e) {
            log.error("Repository: Error finding dictionary item: typeCode={}, itemCode={}, error={}", 
                     typeCode, itemCode, e.getMessage(), e);
            throw new DictionaryRepositoryException("Failed to find dictionary item", e);
        }
    }
}
```

### Contract Layer Components (Shared Query/View Objects)

#### Query Objects for App and Contract Modules
```java
/**
 * Dictionary item query object for CQRS read operations.
 * Shared between app module and contract module for consistent data structure.
 * 
 * @param typeCode dictionary type code
 * @param itemCode dictionary item code
 * @param displayName human-readable display name
 * @param description detailed description
 * @param isDefault whether this is the default item for its type
 * @param sortOrder numeric sort order for display
 * @param enabled whether the item is currently enabled
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryItemQuery(
    String typeCode,
    String itemCode,
    String displayName,
    String description,
    boolean isDefault,
    int sortOrder,
    boolean enabled
);

/**
 * Dictionary type query object for CQRS read operations.
 * Shared between app module and contract module for consistent data structure.
 * 
 * @param typeCode unique dictionary type code
 * @param typeName human-readable type name
 * @param description detailed type description
 * @param enabledItemCount number of enabled items in this type
 * @param hasDefault whether this type has a default item
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryTypeQuery(
    String typeCode,
    String typeName,
    String description,
    int enabledItemCount,
    boolean hasDefault
);

/**
 * Dictionary validation query object for CQRS read operations.
 * Shared between app module and contract module for validation results.
 * 
 * @param typeCode the dictionary type code being validated
 * @param itemCode the dictionary item code being validated
 * @param isValid whether the validation passed
 * @param errorMessage error message if validation failed, null if valid
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryValidationQuery(
    String typeCode,
    String itemCode,
    boolean isValid,
    String errorMessage
);

/**
 * Dictionary health status query object for system monitoring.
 * Shared between app module and contract module for health information.
 * 
 * @param totalTypes total number of dictionary types
 * @param totalItems total number of dictionary items
 * @param enabledItems number of enabled dictionary items
 * @param typesWithoutDefault list of type codes without default items
 * @param typesWithMultipleDefaults list of type codes with multiple default items
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryHealthQuery(
    int totalTypes,
    int totalItems,
    int enabledItems,
    List<String> typesWithoutDefault,
    List<String> typesWithMultipleDefaults
);
```

#### View Objects for Contract Module
```java
/**
 * Dictionary item view object for external subsystem consumption.
 * Used in contract module for clean API boundaries.
 * 
 * @param typeCode dictionary type code
 * @param itemCode dictionary item code
 * @param displayName human-readable display name
 * @param description detailed description
 * @param isDefault whether this is the default item
 * @param sortOrder numeric sort order
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryItemView(
    String typeCode,
    String itemCode,
    String displayName,
    String description,
    boolean isDefault,
    int sortOrder
);

/**
 * Dictionary type view object for external subsystem consumption.
 * Used in contract module for clean API boundaries.
 * 
 * @param typeCode unique dictionary type code
 * @param typeName human-readable type name
 * @param description detailed type description
 * @param itemCount number of available items
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryTypeView(
    String typeCode,
    String typeName,
    String description,
    int itemCount
);
```

### API Layer Components (Following ProvenanceClient Pattern)

#### HTTP API Contract (Internal API Definition)
```java
/**
 * Dictionary HTTP API contract for internal subsystem access.
 * Defines the REST endpoints that subsystems can consume via Feign clients.
 * This interface is strictly read-only following CQRS query patterns.
 * 
 * @author linqibin
 * @since 0.1.0
 */
public interface DictionaryHttpApi {
    
    /** Base path for internal dictionary API endpoints */
    String BASE_PATH = "/_internal/dictionaries";
    
    /**
     * Get dictionary item by type and item code.
     * 
     * @param typeCode the dictionary type code
     * @param itemCode the dictionary item code
     * @return DictionaryItemQuery object if found, null otherwise
     */
    @GetMapping(BASE_PATH + "/types/{typeCode}/items/{itemCode}")
    DictionaryItemQuery getItemByTypeAndCode(@PathVariable("typeCode") String typeCode, 
                                             @PathVariable("itemCode") String itemCode);
    
    /**
     * Get all enabled dictionary items for a specific type.
     * 
     * @param typeCode the dictionary type code
     * @return List of enabled dictionary items, sorted by sort_order then item_code
     */
    @GetMapping(BASE_PATH + "/types/{typeCode}/items")
    List<DictionaryItemQuery> getEnabledItemsByType(@PathVariable("typeCode") String typeCode);
    
    /**
     * Get the default dictionary item for a specific type.
     * 
     * @param typeCode the dictionary type code
     * @return DictionaryItemQuery object if default exists, null otherwise
     */
    @GetMapping(BASE_PATH + "/types/{typeCode}/default")
    DictionaryItemQuery getDefaultItemByType(@PathVariable("typeCode") String typeCode);
    
    /**
     * Validate multiple dictionary references in batch.
     * 
     * @param references list of dictionary references to validate
     * @return List of validation results corresponding to each input reference
     */
    @PostMapping(BASE_PATH + "/validate")
    List<DictionaryValidationQuery> validateReferences(@RequestBody List<DictionaryReference> references);
    
    /**
     * Get dictionary item by external system alias.
     * 
     * @param sourceSystem the external system identifier
     * @param externalCode the external system's code
     * @return DictionaryItemQuery object if alias mapping exists, null otherwise
     */
    @GetMapping(BASE_PATH + "/aliases")
    DictionaryItemQuery getItemByAlias(@RequestParam("sourceSystem") String sourceSystem,
                                       @RequestParam("externalCode") String externalCode);
    
    /**
     * Get all dictionary types in the system.
     * 
     * @return List of all dictionary types, ordered by type_code
     */
    @GetMapping(BASE_PATH + "/types")
    List<DictionaryTypeQuery> getAllTypes();
    
    /**
     * Get dictionary system health status for monitoring.
     * 
     * @return DictionaryHealthQuery containing system health metrics
     */
    @GetMapping(BASE_PATH + "/health")
    DictionaryHealthQuery getHealthStatus();
}
```

#### Feign Client for Subsystem Integration
```java
/**
 * Feign client for dictionary service integration.
 * Subsystems inject this client to access dictionary capabilities via service discovery.
 * Inherits all methods from DictionaryHttpApi for consistent API access.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@FeignClient(
    name = "patra-registry",
    contextId = "dictionaryClient"
)
public interface DictionaryClient extends DictionaryHttpApi {
    // Inherits all methods from DictionaryHttpApi
    // Provides service discovery and load balancing through Spring Cloud
}
```

#### Subsystem Integration Patterns
```java
// Example usage in patra-ingest (similar to ProvenanceClient usage)
@Component
public class PatraRegistryDictionaryPortImpl implements PatraRegistryDictionaryPort {
    
    private final DictionaryClient dictionaryClient;
    private final DictionaryAclConverter converter;
    
    @Override
    public EndpointConfigValidationResult validateEndpointConfig(EndpointConfig config) {
        List<DictionaryReferenceApiReq> references = List.of(
            new DictionaryReferenceApiReq("http_method", config.getHttpMethodCode()),
            new DictionaryReferenceApiReq("endpoint_usage", config.getEndpointUsageCode())
        );
        
        List<DictionaryValidationApiResp> results = dictionaryClient.validateReferences(references);
        return converter.toValidationResult(results);
    }
    
    @Override
    public List<HttpMethodOption> getHttpMethodOptions() {
        List<DictionaryItemApiResp> items = dictionaryClient.getEnabledItemsByType("http_method");
        return converter.toHttpMethodOptions(items);
    }
    
    @Override
    public String getDefaultLifecycleStatus() {
        DictionaryItemApiResp defaultItem = dictionaryClient.getDefaultItemByType("lifecycle_status");
        return defaultItem != null ? defaultItem.itemCode() : "ACTIVE";
    }
}

// Neutral domain models for subsystem use (similar to ProvenanceConfigSnapshot)
public record EndpointConfigValidationResult(boolean isValid, List<String> errors);
public record HttpMethodOption(String code, String displayName);
```

### Adapter Layer Components

#### Internal API Implementation (HTTP Controller)
```java
/**
 * Dictionary API implementation for internal subsystem access.
 * Implements DictionaryHttpApi contract and delegates to application services.
 * Returns contract Query objects directly for consistent API boundaries.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@RestController
public class DictionaryApiImpl implements DictionaryHttpApi {
    
    /** Dictionary query application service */
    private final DictionaryQueryAppService dictionaryQueryAppService;
    
    /** Dictionary validation application service */
    private final DictionaryValidationAppService dictionaryValidationAppService;
    
    /**
     * Get dictionary item by type and item code.
     * 
     * @param typeCode the dictionary type code
     * @param itemCode the dictionary item code
     * @return DictionaryItemQuery object if found, null for 404 handling by Feign
     */
    @Override
    public DictionaryItemQuery getItemByTypeAndCode(String typeCode, String itemCode) {
        log.info("API: Getting dictionary item: typeCode={}, itemCode={}", typeCode, itemCode);
        
        Optional<DictionaryItemQuery> result = dictionaryQueryAppService.findItemByTypeAndCode(typeCode, itemCode);
        if (result.isEmpty()) {
            log.info("API: Dictionary item not found, returning null for 404: typeCode={}, itemCode={}", typeCode, itemCode);
            return null; // Return null for 404 handling by Feign
        }
        
        log.info("API: Successfully returned dictionary item: typeCode={}, itemCode={}", typeCode, itemCode);
        return result.get();
    }
    
    /**
     * Get all enabled dictionary items for a specific type.
     * 
     * @param typeCode the dictionary type code
     * @return List of enabled dictionary items from app service
     */
    @Override
    public List<DictionaryItemQuery> getEnabledItemsByType(String typeCode) {
        return dictionaryQueryAppService.findEnabledItemsByType(typeCode);
    }
    
    /**
     * Get the default dictionary item for a specific type.
     * 
     * @param typeCode the dictionary type code
     * @return DictionaryItemQuery object if default exists, null otherwise
     */
    @Override
    public DictionaryItemQuery getDefaultItemByType(String typeCode) {
        return dictionaryQueryAppService.findDefaultItemByType(typeCode)
            .orElse(null);
    }
    
    /**
     * Validate multiple dictionary references in batch.
     * 
     * @param references list of dictionary references to validate
     * @return List of validation results from validation service
     */
    @Override
    public List<DictionaryValidationQuery> validateReferences(List<DictionaryReference> references) {
        return dictionaryValidationAppService.validateReferences(references);
    }
    
    /**
     * Get dictionary item by external system alias.
     * 
     * @param sourceSystem the external system identifier
     * @param externalCode the external system's code
     * @return DictionaryItemQuery object if alias mapping exists, null otherwise
     */
    @Override
    public DictionaryItemQuery getItemByAlias(String sourceSystem, String externalCode) {
        return dictionaryQueryAppService.findByAlias(sourceSystem, externalCode)
            .orElse(null);
    }
    
    /**
     * Get all dictionary types in the system.
     * 
     * @return List of all dictionary types from query service
     */
    @Override
    public List<DictionaryTypeQuery> getAllTypes() {
        return dictionaryQueryAppService.findAllTypes();
    }
    
    /**
     * Get dictionary system health status for monitoring.
     * 
     * @return DictionaryHealthQuery containing system health metrics
     */
    @Override
    public DictionaryHealthQuery getHealthStatus() {
        return dictionaryValidationAppService.getHealthStatus();
    }
}
```

#### External REST API (Public API)
```java
@RestController
@RequestMapping("/api/registry/dictionaries")
public class DictionaryController {
    
    private final DictionaryApiImpl dictionaryApi;
    
    // Public REST API delegates to internal API implementation
    // Provides external access with proper HTTP status codes and error handling
    
    @GetMapping("/types")
    public ResponseEntity<List<DictionaryTypeApiResp>> getAllTypes() {
        return ResponseEntity.ok(dictionaryApi.getAllTypes());
    }
    
    @GetMapping("/types/{typeCode}/items")
    public ResponseEntity<List<DictionaryItemApiResp>> getItemsByType(@PathVariable String typeCode) {
        return ResponseEntity.ok(dictionaryApi.getEnabledItemsByType(typeCode));
    }
    
    @GetMapping("/types/{typeCode}/items/{itemCode}")
    public ResponseEntity<DictionaryItemApiResp> getItemByTypeAndCode(
        @PathVariable String typeCode, 
        @PathVariable String itemCode) {
        DictionaryItemApiResp item = dictionaryApi.getItemByTypeAndCode(typeCode, itemCode);
        return item != null ? ResponseEntity.ok(item) : ResponseEntity.notFound().build();
    }
    
    @GetMapping("/types/{typeCode}/default")
    public ResponseEntity<DictionaryItemApiResp> getDefaultItem(@PathVariable String typeCode) {
        DictionaryItemApiResp item = dictionaryApi.getDefaultItemByType(typeCode);
        return item != null ? ResponseEntity.ok(item) : ResponseEntity.notFound().build();
    }
    
    @PostMapping("/validate")
    public ResponseEntity<List<DictionaryValidationApiResp>> validateReferences(
        @RequestBody List<DictionaryReferenceApiReq> references) {
        return ResponseEntity.ok(dictionaryApi.validateReferences(references));
    }
    
    @GetMapping("/health")
    public ResponseEntity<DictionaryHealthApiResp> getHealthStatus() {
        return ResponseEntity.ok(dictionaryApi.getHealthStatus());
    }
    
    @GetMapping("/aliases")
    public ResponseEntity<DictionaryItemApiResp> findByAlias(
        @RequestParam String sourceSystem,
        @RequestParam String externalCode) {
        DictionaryItemApiResp item = dictionaryApi.getItemByAlias(sourceSystem, externalCode);
        return item != null ? ResponseEntity.ok(item) : ResponseEntity.notFound().build();
    }
}
```

### Adapter Layer Components

#### REST Controllers
```java
@RestController
@RequestMapping("/api/registry/dictionaries")
public class DictionaryController {
    private final DictionaryQueryAppService dictionaryQueryAppService;
    private final DictionaryValidationAppService dictionaryValidationAppService;
    
    @GetMapping("/types")
    public ResponseEntity<List<DictionaryTypeDTO>> getAllTypes();
    
    @GetMapping("/types/{typeCode}/items")
    public ResponseEntity<List<DictionaryItemDTO>> getItemsByType(@PathVariable String typeCode);
    
    @GetMapping("/types/{typeCode}/items/{itemCode}")
    public ResponseEntity<DictionaryItemDTO> getItemByTypeAndCode(@PathVariable String typeCode, @PathVariable String itemCode);
    
    @GetMapping("/types/{typeCode}/default")
    public ResponseEntity<DictionaryItemDTO> getDefaultItem(@PathVariable String typeCode);
    
    @PostMapping("/validate")
    public ResponseEntity<List<ValidationResult>> validateReferences(@RequestBody List<DictionaryReference> references);
    
    @GetMapping("/health")
    public ResponseEntity<DictionaryHealthStatusDTO> getHealthStatus();
}
```

## Data Models

### Database Schema Integration
The design leverages the existing dictionary schema as documented in the Registry-dict-guide.md:
- `sys_dict_type`: Dictionary type definitions with metadata
- `sys_dict_item`: Dictionary items with business logic constraints and audit fields
- `sys_dict_item_alias`: External system mappings for legacy/third-party integration
- `v_sys_dict_item_enabled`: Optimized read view (enabled=1 AND deleted=0) for performance

### Subsystem Integration Patterns
Business tables reference dictionary items using the `*_code` naming convention:
```sql
-- Example business table with dictionary references
CREATE TABLE reg_prov_endpoint_def (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    http_method_code VARCHAR(32) NOT NULL COMMENT 'DICT CODE: sys_dict_item.item_code (type=http_method)',
    endpoint_usage_code VARCHAR(32) NOT NULL COMMENT 'DICT CODE: sys_dict_item.item_code (type=endpoint_usage)',
    lifecycle_status_code VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'DICT CODE: sys_dict_item.item_code (type=lifecycle_status)',
    -- other fields...
);
```

### Key Constraints and Business Rules
1. **Unique Default Items**: Each type can have at most one default item (enforced by `default_key` generated column)
2. **Soft Delete**: Items are soft-deleted (deleted=1) rather than physically removed
3. **Enable/Disable**: Items can be temporarily disabled (enabled=0) without deletion
4. **Stable Keys**: `item_code` values should remain stable once created for referential integrity
5. **Type Safety**: Application layer validates that item_code belongs to the correct type_code
6. **Zero DDL Evolution**: New dictionary values require only INSERT/UPDATE operations, no schema changes
7. **External Mapping**: Legacy and third-party systems map through alias table, not direct business table changes

## Logging Strategy

### Logging Guidelines
All classes that require logging must use `@Slf4j` annotation for consistent logging approach.

### Log Level Usage
- **ERROR**: System errors, exceptions that affect functionality, database connection failures
- **WARN**: Business rule violations, validation failures, data integrity issues, deprecated usage
- **INFO**: Important business operations, API entry/exit points, significant state changes
- **DEBUG**: Detailed execution flow, parameter values, internal state for troubleshooting

### Logging Examples
```java
@Slf4j
@Service
public class DictionaryQueryAppService {
    
    public Optional<DictionaryItemQuery> findItemByTypeAndCode(String typeCode, String itemCode) {
        // DEBUG: Detailed execution flow with parameters
        log.debug("Finding dictionary item: typeCode={}, itemCode={}", typeCode, itemCode);
        
        try {
            Optional<DictionaryItem> result = dictionaryRepository.findItemByTypeAndCode(typeCode, itemCode);
            
            if (result.isEmpty()) {
                // DEBUG: Expected empty results (not errors)
                log.debug("Dictionary item not found: typeCode={}, itemCode={}", typeCode, itemCode);
            } else {
                // DEBUG: Successful operations with key identifiers
                log.debug("Successfully found dictionary item: typeCode={}, itemCode={}", typeCode, itemCode);
            }
            
            return result.map(dictionaryQueryConverter::toQuery);
            
        } catch (Exception e) {
            // ERROR: Unexpected system errors
            log.error("Failed to find dictionary item: typeCode={}, itemCode={}, error={}", 
                     typeCode, itemCode, e.getMessage(), e);
            throw e;
        }
    }
}

@Slf4j
@RestController
public class DictionaryApiImpl {
    
    @Override
    public DictionaryItemQuery getItemByTypeAndCode(String typeCode, String itemCode) {
        // INFO: API entry points for important operations
        log.info("API: Getting dictionary item: typeCode={}, itemCode={}", typeCode, itemCode);
        
        Optional<DictionaryItemQuery> result = dictionaryQueryAppService.findItemByTypeAndCode(typeCode, itemCode);
        
        if (result.isEmpty()) {
            // INFO: API-level business outcomes
            log.info("API: Dictionary item not found, returning 404: typeCode={}, itemCode={}", typeCode, itemCode);
        }
        
        return result.orElse(null);
    }
}

@Slf4j
@Repository
public class DictionaryRepositoryMpImpl {
    
    @Override
    public DictionaryHealthQuery getHealthStatus() {
        // INFO: System health operations
        log.info("Repository: Generating dictionary health status report");
        
        try {
            // Implementation details...
            
            if (!typesWithMultipleDefaults.isEmpty()) {
                // WARN: Data integrity issues
                log.warn("Repository: Found types with multiple default items: {}", typesWithMultipleDefaults);
            }
            
            return healthStatus;
            
        } catch (Exception e) {
            // ERROR: Infrastructure failures
            log.error("Repository: Failed to generate health status: error={}", e.getMessage(), e);
            throw new DictionaryRepositoryException("Health status generation failed", e);
        }
    }
}
```

### Logging Anti-Patterns to Avoid
- **No log flooding**: Avoid logging in tight loops or high-frequency operations
- **No sensitive data**: Never log passwords, tokens, or PII information
- **No redundant logging**: Don't log the same information at multiple levels
- **No exception swallowing**: Always log exceptions with appropriate context

## Error Handling

### Exception Strategy
- **Common Exceptions**: Generic exceptions placed in patra-common or patra-spring-boot-starter-core modules
- **Domain Exceptions**: Dictionary-specific business rule violations in domain layer
- **Validation Exceptions**: Structured validation results rather than exceptions
- **Infrastructure Exceptions**: Wrapped and translated to domain exceptions with proper logging
- **Graceful Degradation**: Empty results for missing data rather than exceptions

### Error Response Format
```java
public record ErrorResponse(String errorCode, String message, Map<String, Object> details, Instant timestamp);
```

### Common Error Scenarios
1. **Type Not Found**: Return empty list/optional rather than exception
2. **Item Not Found**: Return empty optional with appropriate logging
3. **Multiple Defaults**: Log warning and return first result
4. **Database Connection**: Propagate as infrastructure exception with retry hints