# patra-spring-boot-starter-mybatis

> MyBatis-Plus auto-configuration with pagination, optimistic locking, and performance optimizations.

## 📌 Purpose

Provides **MyBatis-Plus** configuration for repository layer:
- Pagination plugin
- Optimistic locking plugin (`@Version`)
- Batch operations optimization
- JSON type handlers for `JsonNode` fields
- SQL logging (dev/test only)

## 🔧 Auto-Configurations

### MyBatis-Plus Plugins
- **PaginationInnerInterceptor**: Pagination support
- **OptimisticLockerInnerInterceptor**: `@Version` for concurrency control
- **BlockAttackInnerInterceptor**: Prevent full table updates/deletes

### Type Handlers
- **JsonNodeTypeHandler**: Map `JsonNode` ↔ VARCHAR/JSON columns
- Custom type handlers auto-registered

### Global Configuration
- ID generation: `AUTO` (database auto-increment)
- Logic delete support
- Field strategy: `NOT_NULL`

## 🔗 Dependencies

```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-boot-starter-mybatis</artifactId>
</dependency>
```

Includes: MyBatis-Plus, HikariCP, MapStruct

## 🚀 Usage

### Repository Implementation
```java
@Repository
@RequiredArgsConstructor
public class PlanRepositoryMpImpl implements PlanRepository {

    private final PlanMapper mapper;
    private final PlanConverter converter;

    @Override
    public PlanAggregate save(PlanAggregate aggregate) {
        IngestPlanDO entity = converter.toEntity(aggregate);

        if (aggregate.isTransient()) {
            mapper.insert(entity);
            aggregate.assignId(entity.getId());
        } else {
            mapper.updateById(entity);  // Optimistic lock via @Version
        }

        return aggregate;
    }
}
```

### Pagination
```java
Page<IngestPlanDO> page = new Page<>(1, 20);
IPage<IngestPlanDO> result = mapper.selectPage(page, queryWrapper);
```

---

**Last Updated**: 2025-01-12
