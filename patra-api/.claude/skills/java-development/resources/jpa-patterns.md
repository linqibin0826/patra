# JPA 数据访问模式指南

> **目的**: 在六边形架构的基础设施层使用 Spring Data JPA 实现数据持久化

## 🚀 快速开始

### 需要实现新的仓储？

```java
// 1. 创建 JPA Entity
@Entity
@Table(name = "cat_venue", indexes = {
    @Index(name = "uk_issn_l", columnList = "issn_l", unique = true)
})
public class VenueEntity extends BaseJpaEntity {

    @Column(name = "venue_name", length = 500)
    private String venueName;

    @Column(name = "issn_l", length = 9)
    private String issnL;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "JSON")
    private JsonNode metadata;
}

// 2. 创建 Spring Data JPA Repository
public interface VenueDao extends JpaRepository<VenueEntity, Long> {
    Optional<VenueEntity> findByIssnL(String issnL);
    boolean existsByIssnL(String issnL);
}

// 3. 创建 MapStruct 映射器
@Mapper(componentModel = "spring")
public interface VenueJpaMapper {
    VenueEntity toEntity(VenueAggregate aggregate);
    VenueAggregate toAggregate(VenueEntity entity);
}

// 4. 实现仓储接口
@Repository
@RequiredArgsConstructor
public class VenueRepositoryAdapter implements VenueRepository {
    private final VenueDao dao;
    private final VenueJpaMapper mapper;

    @Override
    public VenueAggregate save(VenueAggregate venue) {
        VenueEntity entity = mapper.toEntity(venue);
        entity.assignIdIfMissing();  // 雪花 ID 预分配
        return mapper.toAggregate(dao.save(entity));
    }

    @Override
    public Optional<VenueAggregate> findByIssnL(String issnL) {
        return dao.findByIssnL(issnL).map(mapper::toAggregate);
    }
}
```

---

## 📊 决策矩阵

### 何时使用什么查询方式？

| 场景 | 推荐方式 | 原因 |
|------|---------|------|
| 简单查询 | 方法命名约定 (`findByXxx`) | 开箱即用 |
| 复杂查询 | `@Query` + JPQL | 类型安全 |
| 动态条件 | `Specification` | 条件组合 |
| 原生 SQL | `@Query(nativeQuery = true)` | 特殊优化 |
| 批量操作 | `saveAll()` + flush/clear | 内存控制 |

---

## 🎯 常见场景与模板

### 场景 1: 带 JSON 列的实体

```java
@Entity
@Table(name = "cat_venue")
public class VenueEntity extends BaseJpaEntity {

    // ✅ JSON 列使用 @JdbcTypeCode + columnDefinition
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "JSON")
    private JsonNode metadata;

    // ✅ 嵌入式值对象
    @Embedded
    private AddressEmbeddable address;
}
```

### 场景 2: 批量保存（大数据量）

```java
@Repository
@RequiredArgsConstructor
public class VenueRepositoryAdapter implements VenueRepository {
    private final VenueDao dao;
    private final EntityManager entityManager;

    @Override
    public void saveBatch(List<VenueAggregate> venues) {
        List<VenueEntity> entities = venues.stream()
            .map(mapper::toEntity)
            .peek(VenueEntity::assignIdIfMissing)
            .toList();

        int batchSize = 500;
        for (int i = 0; i < entities.size(); i++) {
            dao.save(entities.get(i));
            if (i > 0 && i % batchSize == 0) {
                entityManager.flush();
                entityManager.clear();  // 防止内存溢出
            }
        }
        entityManager.flush();
    }
}
```

### 场景 3: 枚举类型转换

```java
// AttributeConverter（放在 converter/attribute/ 目录）
@Converter(autoApply = true)
public class VenueTypeAttributeConverter
    implements AttributeConverter<VenueType, String> {

    @Override
    public String convertToDatabaseColumn(VenueType type) {
        return type != null ? type.getCode() : null;
    }

    @Override
    public VenueType convertToEntityAttribute(String code) {
        return VenueType.fromCode(code);
    }
}
```

---

## 📋 速查表

### JPA 注解

| 注解 | 用途 | 示例 |
|------|------|------|
| `@Entity` | 实体标记 | `@Entity` |
| `@Table` | 表名+索引 | `@Table(name="cat_venue", indexes={...})` |
| `@Column` | 字段映射 | `@Column(name="venue_name", length=500)` |
| `@JdbcTypeCode` | JSON 列 | `@JdbcTypeCode(SqlTypes.JSON)` |
| `@Embedded` | 嵌入对象 | `@Embedded private AddressEmbeddable address` |
| `@Version` | 乐观锁 | 继承自 `BaseJpaEntity` |

### 基类选择指南

| 基类 | 字段 | 适用场景 |
|------|------|----------|
| `BaseJpaEntity` | 10 字段 | 聚合根，完整审计 |
| `SoftDeletableJpaEntity` | 11 字段 | 需要软删除的聚合根 |
| `ChildJpaEntity` | 4 字段 | 有独立更新的子实体 |
| `SoftDeletableChildJpaEntity` | 5 字段 | 需要软删除的子实体 |
| `ValueObjectJpaEntity` | 1 字段 | DELETE/INSERT 模式的值对象表 |

### 基础设施层约束

| ✅ 应该 | ❌ 不应该 |
|---------|-----------|
| 根据实体类型选择正确的基类（见上表） | 自定义 ID 生成策略 |
| Dao 继承 `JpaRepository` | 返回 Entity 给上层 |
| 使用 MapStruct 转换 | 手动写转换代码 |
| 仅在软删除实体上使用 `@SQLRestriction` | 对需要软删除的表做物理删除 |
| 大批量操作 flush/clear | 一次性加载全部到内存 |

---

## 🏗️ 核心组件详解

### 1. JPA Entity

```java
@Entity
@Table(name = "cat_author", indexes = {
    @Index(name = "uk_orcid", columnList = "orcid", unique = true),
    @Index(name = "idx_email", columnList = "email")
})
public class AuthorEntity extends BaseJpaEntity {
    // BaseJpaEntity 提供: id, version, createdAt, updatedAt 等
    // 若需要软删除，请改为继承 SoftDeletableJpaEntity（提供 deletedAt）

    @Column(name = "orcid", length = 19)
    private String orcid;

    @Embedded
    private AuthorNameEmbeddable name;
}
```

### 2. Spring Data JPA Repository (Dao)

```java
public interface AuthorDao extends JpaRepository<AuthorEntity, Long> {
    // ✅ 方法命名约定
    Optional<AuthorEntity> findByOrcid(String orcid);
    List<AuthorEntity> findByEmail(String email);
    boolean existsByOrcid(String orcid);

    // ✅ 自定义 JPQL
    @Query("SELECT a FROM AuthorEntity a WHERE a.deletedAt IS NULL")
    List<AuthorEntity> findAllActive(); // 仅适用于继承 SoftDeletableJpaEntity 的实体

    // ✅ 检查是否有数据
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM AuthorEntity a")
    boolean hasAnyData();
}
```

### 3. MapStruct Mapper

```java
@Mapper(componentModel = "spring", unmappedTargetPolicy = IGNORE)
public abstract class AuthorJpaMapper {

    // Entity → Aggregate
    public abstract AuthorAggregate toAggregate(AuthorEntity entity);

    // Aggregate → Entity
    public abstract AuthorEntity toEntity(AuthorAggregate aggregate);

    // ✅ 复杂转换用 @AfterMapping
    @AfterMapping
    protected void afterToEntity(AuthorAggregate agg, @MappingTarget AuthorEntity entity) {
        // 自定义逻辑
    }
}
```

### 4. Repository Adapter

```java
@Repository
@RequiredArgsConstructor
public class AuthorRepositoryAdapter implements AuthorRepository {
    private final AuthorDao dao;
    private final AuthorJpaMapper mapper;

    @Override
    public AuthorAggregate save(AuthorAggregate author) {
        AuthorEntity entity = mapper.toEntity(author);
        entity.assignIdIfMissing();  // 雪花 ID
        return mapper.toAggregate(dao.save(entity));
    }

    @Override
    public Optional<AuthorAggregate> findByOrcid(String orcid) {
        return dao.findByOrcid(orcid).map(mapper::toAggregate);
    }

    @Override
    public boolean existsByOrcid(String orcid) {
        return dao.existsByOrcid(orcid);
    }
}
```

---

## ⚠️ 常见问题与解决

### 问题 1: 乐观锁冲突

**症状**: `OptimisticLockException`

**解决方案**:
```java
try {
    dao.save(entity);
} catch (OptimisticLockException e) {
    // 重新加载并重试，或抛出业务异常
    throw new ConcurrentModificationException("数据已被修改");
}
```

### 问题 2: N+1 查询

**症状**: 循环中触发大量 SQL

**解决方案**:
```java
// ✅ 使用 @EntityGraph
@EntityGraph(attributePaths = {"identifiers", "ratings"})
Optional<VenueEntity> findWithDetailsById(Long id);

// ✅ 或使用 JOIN FETCH
@Query("SELECT v FROM VenueEntity v LEFT JOIN FETCH v.identifiers WHERE v.id = :id")
Optional<VenueEntity> findWithIdentifiersById(@Param("id") Long id);
```

### 问题 3: 批量保存内存溢出

**症状**: `OutOfMemoryError`

**解决方案**:
```java
// ✅ 分批 flush + clear
for (int i = 0; i < entities.size(); i++) {
    dao.save(entities.get(i));
    if (i % 500 == 0) {
        entityManager.flush();
        entityManager.clear();  // 清理一级缓存
    }
}
```

---

## ✅ 最佳实践清单

### 设计原则
- [ ] 根据实体角色选择基类：聚合根用 `BaseJpaEntity`/`SoftDeletableJpaEntity`，子实体用 `ChildJpaEntity`，值对象表用 `ValueObjectJpaEntity`
- [ ] Dao 继承 `JpaRepository`
- [ ] Mapper 使用 MapStruct，放在 `converter/mapper/`
- [ ] AttributeConverter 放在 `converter/attribute/`
- [ ] Repository Adapter 实现 Domain 层 Port 接口

### 性能优化
- [ ] 大批量操作使用 flush/clear
- [ ] 避免 N+1：使用 `@EntityGraph` 或 `JOIN FETCH`
- [ ] 只读查询使用投影或 DTO
- [ ] 配置 `hibernate.jdbc.batch_size=500`

### 数据一致性
- [ ] 使用 `@Version` 乐观锁（BaseJpaEntity 已包含）
- [ ] 软删除仅对需要软删除的实体使用 `@SQLRestriction`
- [ ] ID 使用 `SnowflakeIdGenerator` 预分配

---

## 📚 相关文档

- [commandbus.md](../../rules/tech/commandbus.md) - CommandBus 应用层模式
- [adapter-layer-patterns.md](adapter-layer-patterns.md) - 适配层模式
- [jpa.md](../../rules/tech/jpa.md) - JPA 核心规范
