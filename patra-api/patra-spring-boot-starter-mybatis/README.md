# patra-spring-boot-starter-mybatis

## 概述

持久化层 Starter,基于 MyBatis-Plus 提供自动配置,包括分页、乐观锁、防护拦截器、JSON 类型处理器和审计字段自动填充。

本 Starter 专为基础设施层(Infrastructure Layer)设计,简化仓储实现,提供标准化的持久化能力。

## 核心功能

- **MyBatis-Plus 插件**: 分页、乐观锁、防护拦截器(防止全表更新/删除)
- **JSON 类型处理器**: 自动映射 `JsonNode` ↔ VARCHAR/JSON 列
- **审计字段自动填充**: 自动填充 `createdAt`、`updatedAt` 等时间戳字段
- **Mapper 自动扫描**: 自动扫描 `com.patra.**.infra.persistence.mapper` 包
- **BaseDO 基类**: 提供通用字段(ID、审计字段、乐观锁、逻辑删除)
- **数据层错误映射**: 自动映射 MyBatis-Plus 异常到标准 HTTP 错误码
- **Flyway 数据库迁移**: 自动集成 Flyway 进行 Schema 版本管理

## 自动配置内容

### PatraMybatisAutoConfiguration
提供核心 MyBatis 配置:
- `MapperScannerConfigurer`: 自动扫描 Mapper 接口(`com.patra.**.infra.persistence.mapper`)
- `ConfigurationCustomizer`: 注册自定义 TypeHandler(JsonNode)
- `DataLayerErrorMappingContributor`: 数据层异常到 HTTP 错误码的映射

### MybatisPluginAutoConfig
配置 MyBatis-Plus 插件:
- `MybatisPlusInterceptor`: 插件链(分页、乐观锁、防护)
  - `PaginationInnerInterceptor`: MySQL 分页支持
  - `OptimisticLockerInnerInterceptor`: 乐观锁支持(`@Version`)
  - `BlockAttackInnerInterceptor`: 防止全表更新/删除
- `MetaObjectHandler`: 审计字段自动填充处理器

## 主要组件

### BaseDO (基础实体类)
所有持久化实体(DO)的抽象基类,提供通用字段:
```java
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseDO implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;                        // 主键(雪花算法)

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;              // 创建时间(自动填充)

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;                 // 创建人 ID(自动填充)

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;              // 更新时间(自动填充)

    @Version
    private Long version;                   // 乐观锁版本号

    @TableLogic
    private Boolean deleted;                // 逻辑删除标志
}
```

### AuditMetaObjectHandler (审计字段处理器)
自动填充审计字段,支持时间敏感测试:
- **插入时**: 填充 `createdAt` 和 `updatedAt`
- **更新时**: 填充 `updatedAt`
- 使用注入的 `Clock` 实例,支持测试环境固定时间

### JsonToJsonNodeTypeHandler (JSON 类型处理器)
自动映射 Jackson `JsonNode` 与 SQL JSON/VARCHAR 列:
- **数据库兼容**: MySQL(JSON、TEXT、LONGTEXT)、PostgreSQL(JSON、JSONB)
- **双向转换**: Java JsonNode ↔ SQL JSON 字符串
- **空值处理**: 自动处理 NULL、空字符串和空白字符

## 配置属性

**无专用配置前缀**,使用 MyBatis-Plus 标准配置:

```yaml
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl  # SQL 日志
    map-underscore-to-camel-case: true                   # 下划线转驼峰
  global-config:
    db-config:
      id-type: ASSIGN_ID                                 # 雪花算法 ID
      logic-delete-field: deleted                        # 逻辑删除字段
      logic-delete-value: 1                              # 删除值
      logic-not-delete-value: 0                          # 未删除值
```

## 使用方式

### Maven 依赖
```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-boot-starter-mybatis</artifactId>
</dependency>
```

**传递依赖**(自动包含):
- `patra-spring-boot-starter-core`: 核心基础设施
- `mybatis-plus-spring-boot3-starter`: MyBatis-Plus
- `mybatis-plus-jsqlparser`: SQL 解析(分页等功能必需)
- `flyway-core`, `flyway-mysql`: 数据库迁移
- `mysql-connector-j`: MySQL JDBC 驱动

### 配置示例

**application.yml**:
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/papertrace?useUnicode=true&characterEncoding=utf8
    username: root
    password: password
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5

mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      id-type: ASSIGN_ID
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
  type-handlers-package: com.patra.starter.mybatis.type

flyway:
  enabled: true
  baseline-on-migrate: true
  locations: classpath:db/migration
```

### 代码示例

**实体类(DO)**:
```java
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ingest_plan")
public class IngestPlanDO extends BaseDO {

    @TableField("provenance_code")
    private String provenanceCode;

    @TableField("external_id")
    private String externalId;

    @TableField(value = "metadata", typeHandler = JsonToJsonNodeTypeHandler.class)
    private JsonNode metadata;  // JSON 列,自动序列化/反序列化
}
```

**Mapper 接口**:
```java
@Mapper
public interface PlanMapper extends BaseMapper<IngestPlanDO> {
    // BaseMapper 提供标准 CRUD 方法
    // 自定义方法可在此添加
}
```

**仓储实现**:
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
            mapper.insert(entity);  // createdAt/updatedAt 自动填充
            aggregate.assignId(entity.getId());
        } else {
            mapper.updateById(entity);  // 乐观锁检查,updatedAt 自动更新
        }

        return aggregate;
    }

    @Override
    public Optional<PlanAggregate> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id))
            .map(converter::toDomain);
    }

    @Override
    public Page<PlanAggregate> page(Pageable pageable) {
        Page<IngestPlanDO> page = new Page<>(pageable.getPage(), pageable.getSize());
        IPage<IngestPlanDO> result = mapper.selectPage(page, null);

        return new Page<>(
            result.getRecords().stream()
                .map(converter::toDomain)
                .toList(),
            result.getTotal()
        );
    }
}
```

**Flyway 迁移脚本** (`db/migration/V1__init.sql`):
```sql
CREATE TABLE ingest_plan (
    id BIGINT PRIMARY KEY,
    provenance_code VARCHAR(50) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    metadata JSON,
    created_at TIMESTAMP(6) NOT NULL,
    created_by BIGINT,
    updated_at TIMESTAMP(6) NOT NULL,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    INDEX idx_provenance_external (provenance_code, external_id)
);
```

## 技术栈

- **MyBatis-Plus**: 3.5.9
- **MyBatis**: 3.5.16
- **Flyway**: 11.1.0
- **MySQL Connector/J**: 9.2.0

---

**最后更新**: 2025-01-12
**维护者**: Papertrace Team
