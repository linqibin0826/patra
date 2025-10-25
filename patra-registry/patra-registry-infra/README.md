# patra-registry-infra

Infrastructure layer for patra-registry implementing persistence using MyBatis-Plus.

## Module Overview

This module provides concrete implementations of domain ports defined in `patra-registry-domain`, handling database access and DO↔Domain mapping.

**Key Responsibilities:**
- Implement repository interfaces (`*Port`) from domain layer
- Manage database entities (DO classes) mapped to database tables
- Convert between DO and domain objects using MapStruct
- Execute complex queries using MyBatis-Plus and XML mappers

## Architecture

**Dependencies:**
- ✅ `patra-registry-domain` (implements domain ports)
- ✅ `patra-common` (shared utilities)
- ✅ `patra-spring-boot-starter-core` (core Spring configuration)
- ✅ `patra-spring-boot-starter-mybatis` (MyBatis-Plus configuration)
- ✅ MapStruct (DO↔Domain conversion)

**Critical Rules:**
- ❌ Never expose DO objects outside this module
- ✅ Always use MapStruct converters for DO↔Domain mapping
- ✅ Use JsonNode for JSON columns, convert to String in domain
- ✅ Keep SQL in XML mapper files, not in annotations

## Package Structure

```
com.patra.registry.infra.persistence
├── repository/              # Repository implementations (*RepositoryMpImpl)
├── converter/               # MapStruct converters (DO↔Domain)
├── entity/                  # Database objects (DO)
│   ├── provenance/         # Provenance configuration tables
│   ├── expr/               # Expression metadata tables
│   └── dictionary/         # System dictionary tables
└── mapper/                  # MyBatis mappers (interfaces + XML)
    ├── provenance/
    ├── expr/
    └── dictionary/
```

## DO Entity Conventions

All DO entities follow these patterns:

**Naming:** `Reg*DO` for registry tables, maps to database table names
**Base Class:** Extend `BaseDO` (provides id, created_at, updated_at, deleted)
**Annotations:**
- `@TableName("table_name")` - Database table mapping
- `@TableField("column_name")` - Column mapping
- `@Data` / `@SuperBuilder` - Lombok for getters/setters/builders

**Example:**
```java
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("reg_provenance")
public class RegProvenanceDO extends BaseDO {
    @TableField("provenance_code")
    private String provenanceCode;
    
    @TableField("is_active")
    private Boolean isActive;
}
```

## Mapper Conventions

**Interface Naming:** `Reg*Mapper extends BaseMapper<DO>`
**XML Location:** `src/main/resources/mapper/Reg*Mapper.xml`
**Method Naming:** `select*`, `count*`, `insert*`, `update*`, `delete*`

**Reusable SQL Fragments:**
All provenance configuration mappers use shared SQL fragments:
- `<sql id="activeConfigFilter">` - Temporal slicing and active status filter
- `<sql id="operationPrecedenceOrder">` - Operation-specific precedence ordering

**Example:**
```xml
<select id="selectActiveMerged" resultType="...DO">
    SELECT * FROM table_name
    <include refid="activeConfigFilter"/>
    <include refid="operationPrecedenceOrder"/>
    LIMIT 1
</select>
```

## MapStruct Converters

**Naming:** `*EntityConverter` interface annotated with `@Mapper(componentModel = "spring")`
**Purpose:** Convert DO↔Domain objects, never expose DO outside infra layer

**Key Patterns:**
- Boolean fields: Map SQL `TINYINT(1)` to Java `Boolean` using expressions
- JSON fields: Convert `JsonNode` to `String` using helper method
- Field renames: Use `@Mapping` annotations

**Example:**
```java
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProvenanceEntityConverter {
    @Mapping(target = "code", source = "provenanceCode")
    @Mapping(target = "active", expression = "java(Boolean.TRUE.equals(entity.getIsActive()))")
    Provenance toDomain(RegProvenanceDO entity);
    
    default String map(JsonNode node) {
        return node == null ? null : node.toString();
    }
}
```

## Repository Implementations

**Naming:** `*RepositoryMpImpl implements *Port`
**Annotations:** `@Repository` + `@RequiredArgsConstructor` + `@Slf4j`

**Key Responsibilities:**
1. Inject required mappers and converters
2. Delegate database operations to MyBatis mappers
3. Convert DO to domain objects using converters
4. Add appropriate logging for debugging

**Example:**
```java
@Slf4j
@Repository
@RequiredArgsConstructor
public class ProvenanceConfigRepositoryMpImpl implements ProvenanceConfigRepository {
    private final RegProvenanceMapper provenanceMapper;
    private final ProvenanceEntityConverter converter;
    
    @Override
    public Optional<Provenance> findProvenanceByCode(ProvenanceCode code) {
        log.debug("Finding provenance by code: {}", code.getCode());
        return provenanceMapper.selectByCode(code.getCode()).map(converter::toDomain);
    }
}
```

## Testing Strategy

- **Unit Tests:** Test MapStruct converters (DO↔Domain mapping correctness)
- **Integration Tests:** Located in `patra-registry-boot` module with full Spring context
- **TestContainers:** Use for database integration tests

## Code Quality Standards

This module follows strict refactoring standards:

✅ **Google Java Style Guide compliance**
✅ **All public methods have JavaDoc**
✅ **All methods < 30 lines**
✅ **No duplicate code (DRY principle)**
✅ **SQL fragments reused across mappers**
✅ **DO objects never leaked outside this module**
✅ **MapStruct for all DO↔Domain conversions**

## Recent Refactoring (2025-10)

**SQL Deduplication:**
- Extracted common query patterns into reusable `<sql>` fragments
- Applied across all provenance configuration mappers
- Reduced duplication while maintaining readability

**Code Simplification:**
- Refactored `countNonNullConfigs` to use Stream API
- Improved readability and reduced line count

**JavaDoc Enhancement:**
- Added comprehensive JavaDoc to all mappers
- Documented SQL fragment purposes and usage

## Database Schema

**Provenance Configuration Tables:**
- `reg_provenance` - Root provenance records
- `reg_prov_window_offset_cfg` - Window and offset configuration
- `reg_prov_pagination_cfg` - Pagination strategies
- `reg_prov_http_cfg` - HTTP policies and timeouts
- `reg_prov_batching_cfg` - Batching rules
- `reg_prov_retry_cfg` - Retry and circuit breaker config
- `reg_prov_rate_limit_cfg` - Concurrency and rate limits

**Expression Metadata Tables:**
- `reg_expr_field_dict` - Canonical field definitions
- `reg_prov_expr_capability` - Field capabilities per provenance
- `reg_prov_expr_render_rule` - Expression rendering rules
- `reg_prov_api_param_map` - API parameter mappings

**Dictionary Tables:**
- `sys_dict_type` - Dictionary type definitions
- `sys_dict_item` - Dictionary items
- `sys_dict_item_alias` - External system aliases

## Build Commands

```bash
# Compile this module only
mvn -q compile -pl :patra-registry-infra

# Run unit tests (if any)
mvn test -pl :patra-registry-infra

# Full build with parent
mvn clean install -pl :patra-registry-infra
```

## Dependencies

See `pom.xml` for complete dependency list. Key dependencies:
- MyBatis-Plus (database access)
- MapStruct (object mapping)
- Jackson (JSON handling with JsonNode)
- Lombok (boilerplate reduction)
