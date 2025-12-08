# Patra Spring Boot Starter - MyBatis

Patra 平台的 MyBatis-Plus 集成模块，提供数据访问层的通用配置、审计字段自动填充、JSON 类型处理和异常映射等功能。

## 功能特性

- **自动 Mapper 扫描**: 默认扫描 `com.patra.**.infra.persistence.mapper` 包路径
- **审计字段自动填充**: 自动填充创建时间、更新时间等审计字段
- **JSON 类型处理器**: 支持 `JsonNode` 和 `Map<String, Object>` 与数据库 JSON 字段的映射
- **MyBatis-Plus 插件**: 内置分页、乐观锁、全表操作防护等插件
- **异常映射**: 将数据层异常转换为标准 HTTP 错误码
- **数据库迁移**: 集成 Flyway 数据库版本管理

## 快速开始

### Maven 依赖

```xml
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-spring-boot-starter-mybatis</artifactId>
</dependency>
```

### 基本配置

引入依赖后，自动配置即可生效。如需自定义配置，可在 `application.yml` 中添加：

```yaml
mybatis-plus:
  mapper-locations: classpath*:mapper/**/*.xml
  type-aliases-package: com.patra.**.domain.model
  configuration:
    map-underscore-to-camel-case: true
```

**重要**: 数据库连接 URL 必须包含 `rewriteBatchedStatements=true` 以启用高效批量插入：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/patra?rewriteBatchedStatements=true
```

## 核心组件

### BaseDO - 数据对象基类

所有数据对象（DO）应继承此基类，提供通用字段：

```java
@Data
@SuperBuilder
@TableName("user")
public class UserDO extends BaseDO {
    private String username;
    private String email;
}
```

**内置字段说明:**

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 主键，使用雪花算法生成 |
| `createdAt` | Instant | 创建时间，插入时自动填充 |
| `createdBy` | Long | 创建人ID |
| `createdByName` | String | 创建人名称 |
| `updatedAt` | Instant | 更新时间，插入/更新时自动填充 |
| `updatedBy` | Long | 更新人ID |
| `updatedByName` | String | 更新人名称 |
| `version` | Long | 乐观锁版本号 |
| `ipAddress` | byte[] | 客户端IP地址（二进制存储） |
| `deleted` | Boolean | 软删除标志 |
| `recordRemarks` | String | 备注信息（JSON格式） |

### AuditMetaObjectHandler - 审计字段处理器

自动填充审计字段的元数据处理器：

- **插入时**: 填充 `createdAt`、`updatedAt`
- **更新时**: 填充 `updatedAt`

支持注入自定义 `Clock` 实例，便于时间敏感型测试。

### JSON 类型处理器

#### JsonToJsonNodeTypeHandler

将数据库 JSON 字段映射为 Jackson 的 `JsonNode` 对象：

```java
@TableName(value = "config", autoResultMap = true)
public class ConfigDO extends BaseDO {
    @TableField(typeHandler = JsonToJsonNodeTypeHandler.class)
    private JsonNode settings;
}
```

**支持的数据库类型:**
- MySQL: JSON、TEXT、LONGTEXT
- PostgreSQL: JSON、JSONB

#### JsonToMapTypeHandler

将数据库 JSON 字段映射为 `Map<String, Object>`：

```java
@TableName(value = "metadata", autoResultMap = true)
public class MetadataDO extends BaseDO {
    @TableField(typeHandler = JsonToMapTypeHandler.class)
    private Map<String, Object> attributes;
}
```

### Mapper 接口

所有 Mapper 接口继承 MyBatis-Plus 的 `BaseMapper`：

```java
public interface UserMapper extends BaseMapper<UserDO> {
    // 自定义查询方法
}
```

### 批量插入 - Db.saveBatch()

使用 MyBatis-Plus 3.5.3+ 提供的 `Db.saveBatch()` 静态方法进行批量插入：

```java
import com.baomidou.mybatisplus.extension.toolkit.Db;

// 批量插入（ID 和审计字段自动填充）
List<UserDO> users = createUsers();
Db.saveBatch(users);  // ID 自动回填到 DO 对象

// 回填 ID 到领域对象
for (int i = 0; i < aggregates.size(); i++) {
    aggregates.get(i).assignId(users.get(i).getId());
}
```

**特性:**

| 特性 | 说明 |
|------|------|
| ID 自动生成 | 无需手动调用 `IdWorker.getId()` |
| ID 自动回填 | 插入后 DO 对象的 `id` 字段自动填充 |
| 审计字段填充 | 自动触发 `MetaObjectHandler.insertFill()` |
| 高效批量 SQL | 配合 `rewriteBatchedStatements=true` 生成单条 INSERT 多行 VALUES |

**批量大小建议:**

| 数据量 | 建议 |
|--------|------|
| < 1000 | 直接调用 `Db.saveBatch()` |
| 1K-10K | 分批调用，每批 1000 条 |
| > 10K | 分批调用，需确保 `max_allowed_packet >= 64MB` |

### MybatisPlusInterceptor - 拦截器配置

自动配置以下内置插件：

| 插件 | 功能 |
|------|------|
| `PaginationInnerInterceptor` | 分页查询支持（MySQL方言） |
| `OptimisticLockerInnerInterceptor` | 乐观锁支持 |
| `BlockAttackInnerInterceptor` | 全表更新/删除防护 |

#### 自定义拦截器扩展

通过注册 `InnerInterceptor` Bean 来添加自定义拦截器：

```java
@Bean
public InnerInterceptor myCustomInterceptor() {
    return new MyCustomInterceptor();
}
```

### DataLayerErrorMappingContributor - 异常映射

将数据访问层异常自动映射为标准 HTTP 错误码：

| 异常类型 | HTTP 状态码 | 说明 |
|----------|-------------|------|
| `MybatisPlusException` | 500 | MyBatis-Plus 配置/映射错误 |
| `SQLIntegrityConstraintViolationException` | 409 | 数据完整性约束违反 |
| MySQL 1062 | 409 | 唯一键重复 |
| MySQL 1451/1452 | 409 | 外键约束违反 |
| SQLState 08/HY | 503 | 数据库连接/超时问题 |
| 其他 SQLException | 500 | 未知数据库错误 |

## 自动配置

### PatraMybatisAutoConfiguration

- 配置 Mapper 扫描器，默认路径 `com.patra.**.infra.persistence.mapper`
- 注册 `JsonToJsonNodeTypeHandler` 类型处理器
- 创建数据层错误映射贡献器

**激活条件:** classpath 中存在 `MapperScannerConfigurer`

### MybatisPluginAutoConfig

- 配置 MyBatis-Plus 拦截器链
- 注册审计元数据处理器

## 数据库表设计建议

配合 `BaseDO` 使用时，建议数据库表包含以下字段：

```sql
CREATE TABLE example (
    id BIGINT PRIMARY KEY,
    -- 业务字段 ...

    -- 审计字段
    created_at TIMESTAMP(6) NOT NULL,
    created_by BIGINT,
    created_by_name VARCHAR(64),
    updated_at TIMESTAMP(6) NOT NULL,
    updated_by BIGINT,
    updated_by_name VARCHAR(64),

    -- 控制字段
    version BIGINT DEFAULT 0,
    ip_address VARBINARY(16),
    deleted TINYINT(1) DEFAULT 0,
    record_remarks JSON
);
```

## 依赖关系

此 Starter 传递以下依赖：

- `mybatis-plus-spring-boot3-starter`
- `mybatis-plus-jsqlparser`
- `flyway-core` / `flyway-mysql`
- `mysql-connector-j`
- `jackson-databind`

## 扩展配置

### 自定义 Mapper 扫描路径

通过 MyBatis-Plus 标准配置属性扩展：

```yaml
mybatis-plus:
  mapper-locations: classpath*:mapper/**/*.xml
```

### 注册额外的 TypeHandler

通过 `mybatis-plus.type-handlers-package` 配置自定义 TypeHandler 包路径：

```yaml
mybatis-plus:
  type-handlers-package: com.example.handler
```

### 覆盖默认配置

可通过定义同类型的 Bean 来覆盖默认配置（使用 `@ConditionalOnMissingBean` 条件）。

## 注意事项

1. **事务配置**: 此 Starter 不处理事务或数据源配置，应在业务模块的 `infra/config` 层配置
2. **主键生成**: 默认使用 `IdType.ASSIGN_ID`（雪花算法），需确保已正确配置
3. **软删除**: 使用 `@TableLogic` 注解的 `deleted` 字段，查询时自动过滤已删除记录
4. **乐观锁**: 更新时需确保实体包含 `version` 字段值，否则乐观锁不生效
5. **批量插入**: 必须配置 `rewriteBatchedStatements=true` 才能生成高效的批量 SQL

## 版本信息

- Spring Boot: 3.5.x
- MyBatis-Plus: 3.5.x
- Flyway: 10.x
