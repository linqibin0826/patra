# Patra Spring Boot Starter - JPA

Spring Data JPA Starter，提供基于 Hibernate 7.1 的数据持久化支持，包含雪花 ID 生成、审计、可选软删除和批量写入优化。

## 模块概述

本模块提供 JPA 数据访问层基础设施，支持：

- **BaseJpaEntity 实体基类**：统一的审计字段、乐观锁支持
- **SoftDeletableJpaEntity 软删除基类**：可选的时间戳软删除支持（继承自 BaseJpaEntity）
- **雪花 ID 生成器**：应用层预分配 ID，优化批量插入性能
- **JPA 审计集成**：自动填充 createdAt/createdBy/updatedAt/updatedBy
- **Hibernate 批量写入优化**：开箱即用的批量插入/更新配置
- **错误映射**：通过 `ErrorMappingContributor` SPI 将 JPA/Hibernate 异常转换为标准错误码
- **Flyway 数据库迁移**：内置 Flyway + MySQL 支持

## 核心功能

### 自动配置

| 配置类 | 功能 | 条件 |
|--------|------|------|
| `PatraJpaAutoConfiguration` | 主配置类，导入审计和 Hibernate 优化配置 | JpaRepository 类存在 |
| `JpaAuditingConfig` | 启用 JPA 审计，配置 AuditorAware 和 DateTimeProvider | 由主配置导入 |
| `HibernatePropertiesCustomizer` | Hibernate 7.1 批量写入和性能优化配置 | 由主配置导入 |

### 组件说明

| 组件 | 职责 |
|------|------|
| `BaseJpaEntity` | JPA 实体基类，提供 ID、审计字段、乐观锁 |
| `SoftDeletableJpaEntity` | 软删除实体基类，使用 Hibernate 原生 `@SoftDelete` 注解 |
| `SoftDeletableChildJpaEntity` | 软删除子实体基类，适用于有独立更新语义的子表 |
| `SnowflakeIdGenerator` | 雪花算法 ID 生成器，单例模式，线程安全 |
| `JpaErrorMappingContributor` | JPA/Hibernate/SQL 异常到标准错误码的映射 |
| `JpaAuditingConfig` | Spring Data JPA 审计配置 |
| `HibernatePropertiesCustomizer` | Hibernate 属性定制器 |

## BaseJpaEntity 实体基类

所有 JPA Entity **必须**继承 `BaseJpaEntity`（或其子类 `SoftDeletableJpaEntity`），它提供以下字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 主键，使用雪花 ID 预分配 |
| `createdAt` | Instant | 创建时间，自动填充 |
| `createdBy` | Long | 创建人 ID，自动填充 |
| `createdByName` | String | 创建人名称，需手动或回调填充 |
| `updatedAt` | Instant | 更新时间，自动填充 |
| `updatedBy` | Long | 更新人 ID，自动填充 |
| `updatedByName` | String | 更新人名称，需手动或回调填充 |
| `version` | Long | 乐观锁版本号，自动管理 |
| `recordRemarks` | String | 备注/审计追踪（JSON 格式） |
| `ipAddress` | byte[] | 请求来源 IP（二进制存储） |

## SoftDeletableJpaEntity 软删除基类

需要软删除功能的实体应继承 `SoftDeletableJpaEntity`，它基于 Hibernate 原生 `@SoftDelete` 注解实现时间戳策略的软删除。

### 软删除机制

使用 Hibernate 7.x 原生 `@SoftDelete(strategy = SoftDeleteType.TIMESTAMP)` 注解：

```java
// 执行软删除（直接调用 delete，Hibernate 自动转换为 UPDATE）
repository.delete(entity);
// 或
entityManager.remove(entity);
// 实际执行: UPDATE xxx SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?

// 查询自动排除已删除记录（Hibernate 自动添加条件）
repository.findAll();  // WHERE deleted_at IS NULL
```

**实现原理**：

- `deleted_at` 列由 Hibernate 自动管理，**不是实体字段**
- `DELETE` 语句自动转换为 `UPDATE deleted_at = CURRENT_TIMESTAMP`
- 所有查询自动添加 `WHERE deleted_at IS NULL` 条件

### 何时使用软删除？

| 场景 | 推荐基类 | 说明 |
|------|---------|------|
| 聚合根（如 Venue、Publication、Plan、Task） | `SoftDeletableJpaEntity` | 需要保护数据完整性 |
| 被引用的配置数据（如 Provenance、SysDictType） | `SoftDeletableJpaEntity` | 可能被外键引用 |
| 需要软删除的子实体（有独立更新语义） | `SoftDeletableChildJpaEntity` | 子表需保留删除历史 |
| 子表/外部数据快照（如 VenueMesh、TaskRun） | `BaseJpaEntity` | 使用物理删除，简化逻辑 |

### 基类体系对比

| 基类 | 字段数 | 软删除 | 审计字段 | 适用场景 |
|------|-------|--------|---------|----------|
| `BaseJpaEntity` | 10 | ❌ | 完整（含 createdBy/updatedBy） | 聚合根，完整审计 |
| `SoftDeletableJpaEntity` | 10 | ✅ | 完整 | 需要软删除的聚合根 |
| `ChildJpaEntity` | 4 | ❌ | 仅时间戳 | 子实体，有独立更新 |
| `SoftDeletableChildJpaEntity` | 4 | ✅ | 仅时间戳 | 需要软删除的子实体 |
| `ValueObjectJpaEntity` | 1 | ❌ | 无 | 值对象表，DELETE/INSERT |

### Entity 示例

**普通实体（无软删除）**：

```java
@Entity
@Table(name = "cat_mesh_qualifier")
public class MeshQualifierEntity extends BaseJpaEntity {

    @Column(name = "ui", nullable = false, unique = true)
    private String ui;

    @Column(name = "name", nullable = false)
    private String name;
}
```

**聚合根实体（需要软删除）**：

```java
@Entity
@Table(name = "cat_venue")
public class VenueEntity extends SoftDeletableJpaEntity {

    @Column(name = "title", nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "venue_type", nullable = false)
    private VenueType venueType;
}
```

## 雪花 ID 生成器

`SnowflakeIdGenerator` 提供分布式唯一 ID 生成，**必须在应用层预分配 ID**：

### 算法结构（64 位）

```
| 1 bit | 41 bits    | 5 bits      | 5 bits    | 12 bits  |
| 符号  | 时间戳     | 数据中心 ID | 机器 ID   | 序列号   |
```

### 特性

- **时间戳**：毫秒级精度，支持约 69 年（从 2024-01-01 开始）
- **机器标识**：自动从 MAC 地址派生，支持 1024 个节点
- **序列号**：每毫秒 4096 个 ID，单节点 QPS 约 400 万
- **时钟回退保护**：小幅回退时等待，大幅回退时抛出异常
- **线程安全**：通过 synchronized 保证并发安全

### 使用方式

```java
// 获取 Long 类型 ID
Long id = SnowflakeIdGenerator.getId();
entity.setId(id);

// 获取 String 类型 ID
String idStr = SnowflakeIdGenerator.getIdStr();
```

> **为什么不用数据库自增？**
>
> 数据库自增 ID 会破坏 JPA 的批量插入优化。Hibernate 需要在 `persist()` 时知道实体 ID，
> 如果使用自增 ID，每次插入都需要立即执行 SQL 获取生成的 ID，无法批量执行。

## JPA 审计配置

自动填充审计字段，通过 `@EnableJpaAuditing` 启用：

```yaml
# 审计功能开箱即用，无需额外配置
```

### 自定义审计用户

默认的 `AuditorAware` 返回空 Optional（系统操作）。应用应覆盖此 Bean：

```java
@Bean
public AuditorAware<Long> auditorAware() {
    return () -> Optional.ofNullable(SecurityContextHolder.getContext())
        .map(SecurityContext::getAuthentication)
        .filter(Authentication::isAuthenticated)
        .map(auth -> ((UserDetails) auth.getPrincipal()).getId());
}
```

### 自定义时钟（测试用）

```java
@Bean
public Clock clock() {
    return Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);
}
```

## Hibernate 批量写入优化

`HibernatePropertiesCustomizer` 提供以下默认配置：

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `hibernate.jdbc.batch_size` | 500 | 批量写入大小 |
| `hibernate.order_inserts` | true | 按实体类型排序 INSERT |
| `hibernate.order_updates` | true | 按实体类型排序 UPDATE |
| `hibernate.jdbc.batch_versioned_data` | true | 支持带版本号实体的批量更新 |
| `hibernate.cache.use_second_level_cache` | false | 禁用二级缓存（批量场景优化） |
| `hibernate.cache.use_query_cache` | false | 禁用查询缓存 |

### 覆盖配置

通过 `application.yml` 覆盖：

```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 1000  # 调整批量大小
```

### 批量写入最佳实践

```java
@Service
public class BatchImportService {
    private final EntityManager em;
    private static final int BATCH_SIZE = 500;

    @Transactional
    public void batchImport(List<MeshQualifierEntity> entities) {
        for (int i = 0; i < entities.size(); i++) {
            // 预分配雪花 ID
            entities.get(i).setId(SnowflakeIdGenerator.getId());
            em.persist(entities.get(i));

            // 每 BATCH_SIZE 条刷新一次，防止内存溢出
            if (i > 0 && i % BATCH_SIZE == 0) {
                em.flush();
                em.clear();
            }
        }
        em.flush();
        em.clear();
    }
}
```

## 错误映射

`JpaErrorMappingContributor` 将数据访问层异常转换为标准 HTTP 错误码：

| 异常类型 | HTTP 状态 | 说明 |
|---------|----------|------|
| `EntityNotFoundException` | 404 | 实体不存在 |
| `EntityExistsException` | 409 | 实体已存在（重复键） |
| `OptimisticLockException` | 409 | 乐观锁冲突 |
| `DataIntegrityViolationException` | 409 | 数据完整性违反（包含 PG SQLState `23505 unique_violation`、`23503 foreign_key_violation`、`23502 not_null_violation`、`23514 check_violation`） |
| `ConstraintViolationException` | 409 | 约束违反 |
| `JDBCConnectionException` | 503 | 数据库连接问题 |
| `QueryTimeoutException` | 503 | 查询超时 |
| 其他 `PersistenceException` | 500 | 内部错误 |
| SQLState `08xxx` / `HYxxx` | 503 | 连接/超时类错误（兜底） |

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>dev.linqibin.patra</groupId>
    <artifactId>patra-spring-boot-starter-jpa</artifactId>
</dependency>
```

### 2. 配置数据源

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/patra?rewriteBatchedStatements=true
    username: root
    password: password
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: validate  # 使用 Flyway 管理 DDL
    show-sql: false
  flyway:
    enabled: true
    locations: classpath:db/migration
```

> **重要**：数据库连接 URL 必须包含 `rewriteBatchedStatements=true` 以启用 MySQL 批量写入优化。

### 3. 创建 Entity

```java
@Entity
@Table(name = "ing_task")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class TaskEntity extends BaseJpaEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TaskStatus status;
}
```

### 4. 创建 Dao

```java
public interface TaskDao extends JpaRepository<TaskEntity, Long> {

    Optional<TaskEntity> findByName(String name);

    @Query("SELECT t FROM TaskEntity t WHERE t.status = :status")
    List<TaskEntity> findByStatus(@Param("status") TaskStatus status);
}
```

### 5. 实现 Repository 适配器

```java
@Repository
@RequiredArgsConstructor
public class JpaTaskRepository implements TaskRepository {

    private final TaskDao dao;
    private final TaskJpaMapper mapper;

    @Override
    public Task save(Task task) {
        TaskEntity entity = mapper.toEntity(task);
        if (entity.getId() == null) {
            entity.setId(SnowflakeIdGenerator.getId());
        }
        return mapper.toDomain(dao.save(entity));
    }

    @Override
    public Optional<Task> findById(Long id) {
        return dao.findById(id).map(mapper::toDomain);
    }
}
```

## 配置参考

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `spring.datasource.url` | String | - | 数据库连接 URL（**必须含** `rewriteBatchedStatements=true`） |
| `spring.jpa.hibernate.ddl-auto` | String | validate | DDL 策略，建议使用 Flyway |
| `spring.jpa.properties.hibernate.jdbc.batch_size` | int | 500 | 批量写入大小 |
| `spring.flyway.enabled` | boolean | true | 是否启用 Flyway |
| `spring.flyway.locations` | String | classpath:db/migration | 迁移脚本位置 |

## 依赖关系

```
patra-spring-boot-starter-jpa
├── patra-common-core                    # 公共工具、错误码定义
├── patra-spring-boot-starter-core       # 核心 Starter（ErrorMappingContributor SPI）
├── spring-boot-starter-data-jpa         # Spring Data JPA（含 Hibernate 7.1）
├── flyway-core                          # Flyway 核心
├── flyway-mysql                         # Flyway MySQL 支持
├── mysql-connector-j                    # MySQL 驱动
└── jackson-databind                     # JSON 处理（用于 JSON 类型字段）
```

## 包结构

```
dev.linqibin.starter.jpa
├── autoconfig/
│   ├── PatraJpaAutoConfiguration       # 主自动配置类
│   ├── JpaAuditingConfig               # JPA 审计配置
│   └── HibernatePropertiesCustomizer   # Hibernate 属性定制器
├── entity/
│   ├── BaseJpaEntity                   # JPA 实体基类（审计 + 乐观锁）
│   ├── ChildJpaEntity                  # 子实体基类（仅 ID + 时间戳 + 版本号）
│   ├── SoftDeletableJpaEntity          # 软删除聚合根基类（@SoftDelete）
│   ├── SoftDeletableChildJpaEntity     # 软删除子实体基类（@SoftDelete）
│   └── ValueObjectJpaEntity            # 值对象实体基类（仅 ID）
├── error/
│   └── contributor/
│       └── JpaErrorMappingContributor  # JPA 异常映射贡献器
└── id/
    └── SnowflakeIdGenerator            # 雪花 ID 生成器
```

## 设计原则

1. **应用层 ID 预分配**：使用雪花 ID，保证批量插入性能
2. **可选软删除**：通过继承 `SoftDeletableJpaEntity` 按需启用软删除
3. **统一审计**：所有实体自动记录创建/更新时间和操作人
4. **乐观锁保护**：防止并发更新冲突
5. **批量优化**：默认配置 Hibernate 批量写入，与 Spring Batch 对齐

## 常见问题

### Q: 为什么 Entity 必须继承 BaseJpaEntity？

**A**: `BaseJpaEntity` 提供项目统一的基础设施：
- 雪花 ID（避免自增破坏批量插入）
- 审计字段（自动填充创建/更新时间和操作人）
- 乐观锁（防止并发更新冲突）

### Q: 何时使用 SoftDeletableJpaEntity？

**A**: 聚合根和被外键引用的配置数据应使用 `SoftDeletableJpaEntity`，子表和外部数据快照使用 `BaseJpaEntity`（物理删除）。

### Q: 如何查询已删除的记录？

**A**: Hibernate 的 `@SoftDelete` 会自动过滤已删除记录。如需查询（如审计场景），使用 Native Query 绕过过滤：

```java
// 查询所有已删除记录
@Query(value = "SELECT * FROM cat_venue WHERE deleted_at IS NOT NULL",
       nativeQuery = true)
List<VenueEntity> findDeleted();

// 查询包含已删除的所有记录
@Query(value = "SELECT * FROM cat_venue",
       nativeQuery = true)
List<VenueEntity> findAllIncludingDeleted();
```

### Q: 为什么使用 @SoftDelete 而不是 @SQLRestriction？

**A**: Hibernate 7.x 的 `@SoftDelete` 现已支持 `SoftDeleteType.TIMESTAMP` 时间戳策略，相比自定义 `@SQLRestriction` 方案：
- 自动将 `DELETE` 转换为 `UPDATE`，无需手动调用 `softDelete()` 方法
- 框架原生支持，减少自定义代码维护成本
- 与 Hibernate 查询优化器更好集成

### Q: 批量插入性能不佳？

**A**: 检查以下配置：
1. 数据库 URL 是否包含 `rewriteBatchedStatements=true`
2. 是否使用 `SnowflakeIdGenerator.getId()` 预分配 ID
3. 大批量（> 500 条）是否定期 `flush()` + `clear()`

---

**最后更新**: 2026-01-11
**维护者**: Patra Team
