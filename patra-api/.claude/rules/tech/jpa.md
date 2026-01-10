---
paths: patra-*/*-infra/**/*.java, patra-spring-boot-starter-jpa/**/*.java
---

# JPA 使用规范

## 基类选择指南

根据实体在 DDD 中的角色选择合适的基类：

| 基类 | 字段 | 适用场景 | 示例 |
|------|------|----------|------|
| **BaseJpaEntity** | 10 字段（完整审计） | 聚合根，需要完整审计追踪 | VenueEntity, AuthorEntity |
| **SoftDeletableJpaEntity** | 11 字段（+软删除） | 需要软删除的聚合根 | PublicationEntity, PlanEntity |
| **ChildJpaEntity** | 4 字段（id/version/时间戳） | 有独立更新语义的子实体，需增量同步 | TaskRunEntity, VenueRatingEntity |
| **SoftDeletableChildJpaEntity** | 5 字段（+软删除） | 需要软删除的子实体 | （按需使用） |
| **ValueObjectJpaEntity** | 1 字段（仅 id） | 采用 DELETE/INSERT 模式的值对象表 | VenueIdentifierEntity, MeshTreeNumberEntity |

### 选择决策树

```
实体是否为聚合根？
├── 是 → 是否需要软删除？
│         ├── 是 → SoftDeletableJpaEntity
│         └── 否 → BaseJpaEntity
└── 否 → 是否有独立更新语义？（需要 version 或增量同步）
          ├── 是 → 是否需要软删除？
          │         ├── 是 → SoftDeletableChildJpaEntity
          │         └── 否 → ChildJpaEntity
          └── 否 → ValueObjectJpaEntity（DELETE/INSERT 模式）
```

### 各基类字段对比

| 字段 | BaseJpaEntity | SoftDeletableJpaEntity | ChildJpaEntity | SoftDeletableChildJpaEntity | ValueObjectJpaEntity |
|------|:-------------:|:----------------------:|:--------------:|:---------------------------:|:--------------------:|
| id | ✓ | ✓ | ✓ | ✓ | ✓ |
| version | ✓ | ✓ | ✓ | ✓ | - |
| createdAt | ✓ | ✓ | ✓ | ✓ | - |
| updatedAt | ✓ | ✓ | ✓ | ✓ | - |
| createdBy/ByName | ✓ | ✓ | - | - | - |
| updatedBy/ByName | ✓ | ✓ | - | - | - |
| recordRemarks | ✓ | ✓ | - | - | - |
| ipAddress | ✓ | ✓ | - | - | - |
| deletedAt | - | ✓ | - | ✓ | - |

## 核心规范

1. **继承规则**：根据上述决策树选择正确的基类
2. **命名规则**：`{Name}Entity`、`{Name}Dao`、`{Name}JpaMapper`、`{Type}AttributeConverter`
3. **对象转换**：Entity ↔ Aggregate 转换使用 MapStruct，禁止手动编写
4. **批量保存**：使用 `saveAll()`，大批量（> 500 条）需手动 `flush()` + `clear()` 防止内存溢出
5. **ID 预分配**：使用 `SnowflakeIdGenerator.getId()` 预分配，禁止数据库自增
6. **事务边界**：只在 Application 层使用 `@Transactional`，Infrastructure 层禁止声明事务
7. **连接配置**：数据库连接 URL 必须包含 `rewriteBatchedStatements=true`

## 特殊场景说明

### ValueObjectJpaEntity 使用模式

值对象表采用 DELETE/INSERT 模式，典型操作：

```java
// 聚合根保存时的典型操作（全删全增）
venueIdentifierRepository.deleteByVenueId(venue.getId());
venueIdentifierRepository.saveAll(newIdentifiers);
```

### ChildJpaEntity 使用场景

- 有独立更新语义（如任务执行记录可独立更新状态）
- 需要支持增量同步（通过 createdAt/updatedAt 做变更检测）
- 可能存在并发更新（需要 version 乐观锁保护）

### SoftDeletableChildJpaEntity 使用场景

- 有独立更新语义且需要软删除的子实体
- 需要保留删除历史记录的子表
- 子实体删除后需要可恢复或用于审计
