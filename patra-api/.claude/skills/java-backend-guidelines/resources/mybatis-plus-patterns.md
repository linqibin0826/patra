# MyBatis-Plus Patterns & Database Access

**Purpose**: Infrastructure layer patterns for database access using MyBatis-Plus.

---

## Content Outline

1. **Repository Implementation Pattern**
   - Implement domain ports
   - DO ↔ Domain conversion (MapStruct)
   - Example: ProvenanceRepositoryImpl

2. **MyBatis-Plus DOs**
   - `@TableName`, `@TableId`, `@TableField`
   - `@TableLogic` for soft delete
   - `@Version` for optimistic locking
   - JsonNode for complex JSON fields

3. **MyBatis-Plus Query API**
   - Lambda-based API (`LambdaQueryWrapper`)
   - Avoid deprecated `QueryWrapper`
   - Batch operations

4. **Complex Queries (XML Mappers)**
   - When to use XML (3+ conditions)
   - Logical delete handling
   - Custom result mapping

5. **MapStruct Converters**
   - DO → Domain conversion
   - Domain → DO conversion
   - Handling enums and value objects

6. **Performance Patterns**
   - Avoid N+1 queries
   - Batch inserts/updates
   - Proper indexing

---

## Key Principles

- ✅ Implement domain port interfaces
- ✅ Use MapStruct for conversions
- ✅ MyBatis-Plus for simple queries, XML for complex
- ❌ NEVER expose DOs outside infrastructure layer

---

**📝 Status**: Content outline created. Full examples from ProvenanceRepositoryImpl, OutboxMessageRepositoryImpl to be added.

**See Also**: [architecture-overview.md](architecture-overview.md) for infrastructure layer details.
