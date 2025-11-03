# patra-registry-infra

## 概述

`patra-registry-infra` 是 patra-registry 服务的**基础设施层模块**,负责实现领域层定义的仓储接口,提供数据库持久化能力。本模块使用 MyBatis-Plus 作为 ORM 框架,通过 MapStruct 转换器隔离数据库实体(DO)和领域对象。

在六边形架构中,本模块实现领域层的出站端口,处理所有与外部系统(数据库)的交互,确保领域层的纯粹性。

## 核心职责

- **仓储实现**: 实现领域层的仓储接口(`ProvenanceConfigRepository`、`ExprRepository`)
- **数据库访问**: 使用 MyBatis-Plus Mapper 执行 SQL 查询和数据持久化
- **对象转换**: 通过 MapStruct 转换器在 DO 和领域对象之间转换
- **时态查询**: 实现时态切片逻辑,查询指定时刻有效的配置
- **作用域优先级**: 实现配置作用域优先级规则(TASK > OPERATION > SOURCE)

## 模块结构

```
patra-registry-infra/
└── src/main/java/com/patra/registry/infra/
    └── persistence/
        ├── repository/                     # 仓储实现
        │   ├── ProvenanceConfigRepositoryMpImpl.java
        │   └── ExprRepositoryMpImpl.java
        ├── converter/                      # MapStruct 转换器
        │   ├── ProvenanceEntityConverter.java
        │   └── ExprEntityConverter.java
        ├── entity/                         # 数据库实体(DO)
        │   ├── provenance/                 # 数据源配置 DO
        │   ├── expr/                       # 表达式元数据 DO
        │   └── dictionary/                 # 系统字典 DO
        └── mapper/                         # MyBatis Mapper
            ├── provenance/                 # 数据源 Mapper + XML
            ├── expr/                       # 表达式 Mapper + XML
            └── dictionary/                 # 字典 Mapper + XML
```

## 主要组件

### ProvenanceConfigRepositoryMpImpl

数据源配置仓储的 MyBatis 实现,负责查询数据源元数据和配置。

**核心方法**:
- `findProvenanceByCode(ProvenanceCode)`: 根据代码查询数据源
- `findAllProvenances()`: 查询所有激活的数据源
- `findActiveWindowOffset(Long, String, Instant)`: 查询有效的时间窗口偏移配置
- `loadConfiguration(Long, String, Instant)`: 加载完整配置聚合

**设计模式**:
- 使用泛型方法 `findActiveConfig` 统一处理所有配置维度的查询
- 通过 `ConfigSelector` 函数式接口抽象 Mapper 查询逻辑
- 使用 `atOrNow` 方法统一时态参数处理

**使用示例**:
```java
@Repository
@RequiredArgsConstructor
public class ProvenanceConfigRepositoryMpImpl implements ProvenanceConfigRepository {

  private final RegProvenanceMapper provenanceMapper;
  private final ProvenanceEntityConverter converter;

  @Override
  public Optional<Provenance> findProvenanceByCode(ProvenanceCode code) {
    return provenanceMapper.selectByCode(code.getCode()).map(converter::toDomain);
  }

  @Override
  public Optional<ProvenanceConfiguration> loadConfiguration(
      Long provenanceId, String operationType, Instant at) {
    Optional<Provenance> provenanceOpt = findProvenanceById(provenanceId);
    if (provenanceOpt.isEmpty()) {
      return Optional.empty();
    }

    Instant timestamp = atOrNow(at);
    Provenance provenance = provenanceOpt.get();
    return Optional.of(assembleConfiguration(provenance, operationType, timestamp));
  }
}
```

### ProvenanceEntityConverter

MapStruct 转换器,将数据源数据库实体(DO)转换为领域对象。

**转换方法**:
- `toDomain(RegProvenanceDO)`: 转换数据源 DO
- `toDomain(RegProvWindowOffsetCfgDO)`: 转换时间窗口偏移配置 DO
- `toDomain(RegProvPaginationCfgDO)`: 转换分页配置 DO
- `toDomain(RegProvHttpCfgDO)`: 转换 HTTP 配置 DO
- ... (其他配置维度转换)

**设计要点**:
- 使用 `@Mapping` 注解处理字段映射
- 使用 `expression` 处理布尔转换(`TINYINT(1)` → `Boolean`)
- 使用辅助方法处理 JSON 字段(`JsonNode` → `String`)

**示例**:
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

### MyBatis Mapper

所有 Mapper 接口遵循统一命名和查询模式:

**命名规范**:
- 接口: `Reg*Mapper extends BaseMapper<DO>`
- XML: `src/main/resources/mapper/Reg*Mapper.xml`
- 方法: `select*`、`count*`、`insert*`、`update*`、`delete*`

**时态查询 SQL 片段**:
```xml
<sql id="activeConfigFilter">
    WHERE provenance_id = #{provenanceId}
      AND deleted = 0
      AND effective_from &lt;= #{at}
      AND (effective_until IS NULL OR effective_until &gt; #{at})
</sql>

<sql id="operationPrecedenceOrder">
    ORDER BY
        CASE
            WHEN operation_type = #{operationType} THEN 1
            WHEN operation_type = 'ALL' THEN 2
            ELSE 3
        END
</sql>
```

**使用示例**:
```xml
<select id="selectActiveMerged" resultType="RegProvPaginationCfgDO">
    SELECT * FROM reg_prov_pagination_cfg
    <include refid="activeConfigFilter"/>
    <include refid="operationPrecedenceOrder"/>
    LIMIT 1
</select>
```

## 数据库实体(DO)

所有 DO 实体遵循统一规范:

**命名**: `Reg*DO`,映射到数据库表名
**基类**: 扩展 `BaseDO`(提供 `id`、`createdAt`、`updatedAt`、`deleted`)
**注解**:
- `@TableName("table_name")`: 数据库表映射
- `@TableField("column_name")`: 列映射
- `@Data` / `@SuperBuilder`: Lombok 生成 getter/setter/builder

**示例**:
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

## 依赖关系

**上游依赖**:
- `patra-registry-domain`: 领域模型和仓储接口
- `patra-common-core`: 共享枚举和工具类
- `patra-spring-boot-starter-core`: Spring 核心配置
- `patra-spring-boot-starter-mybatis`: MyBatis-Plus 配置
- `org.mapstruct:mapstruct`: 对象映射框架

**下游消费者**:
- `patra-registry-boot`: 组装本模块并提供 Spring 上下文

## 设计原则

### 1. DO 不泄漏

- DO 对象仅在 `infra` 模块内部使用
- 对外仅暴露领域对象,通过转换器隔离
- 保护领域层不受持久化细节影响

### 2. 时态查询

- 所有配置表包含 `effectiveFrom` 和 `effectiveUntil` 字段
- 通过 `at` 参数查询指定时刻有效的配置
- SQL 片段复用,确保一致性

### 3. 作用域优先级

配置按作用域分为三级(优先级从高到低):
- **TASK 级**: 任务特定配置
- **OPERATION 级**: 操作类型特定配置(HARVEST、UPDATE)
- **SOURCE 级**: 数据源默认配置

**查询逻辑**: 通过 `ORDER BY CASE` 实现优先级排序,`LIMIT 1` 获取最高优先级配置

### 4. 泛型抽象

使用泛型方法 `findActiveConfig` 统一处理所有配置维度的查询,减少重复代码:
```java
private <DO, DOMAIN> Optional<DOMAIN> findActiveConfig(
    Long provenanceId,
    String operationType,
    Instant at,
    String configName,
    ConfigSelector<DO> selector,
    Function<DO, DOMAIN> converter) {
    // 统一查询逻辑
}
```

## 数据库表结构

**数据源配置表**:
- `reg_provenance`: 根数据源记录
- `reg_prov_window_offset_cfg`: 窗口和偏移配置
- `reg_prov_pagination_cfg`: 分页策略
- `reg_prov_http_cfg`: HTTP 策略和超时
- `reg_prov_batching_cfg`: 批处理规则
- `reg_prov_retry_cfg`: 重试和熔断器配置
- `reg_prov_rate_limit_cfg`: 并发和速率限制

**表达式元数据表**:
- `reg_expr_field_dict`: 规范字段定义
- `reg_prov_expr_capability`: 每个数据源的字段能力
- `reg_prov_expr_render_rule`: 表达式渲染规则
- `reg_prov_api_param_map`: API 参数映射

**字典表**:
- `sys_dict_type`: 字典类型定义
- `sys_dict_item`: 字典项
- `sys_dict_item_alias`: 外部系统别名

## 相关文档

- [patra-registry 顶层文档](../README.md)
- [patra-registry-domain 模块](../patra-registry-domain/README.md) - 仓储接口的定义方
- [patra-registry-boot 模块](../patra-registry-boot/README.md) - 本模块的组装方

---

**最后更新**: 2025-01-12
