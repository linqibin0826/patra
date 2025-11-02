# patra-registry-infra

patra-registry 的基础设施层,使用 MyBatis-Plus 实现持久化。

## 模块概览

本模块提供 `patra-registry-domain` 中定义的领域端口的具体实现,处理数据库访问和 DO↔Domain 映射。

**核心职责:**
- 实现领域层的仓储接口(`*Port`)
- 管理数据库实体(DO 类),映射到数据库表
- 使用 MapStruct 在 DO 和领域对象之间转换
- 使用 MyBatis-Plus 和 XML 映射器执行复杂查询

## 架构

**依赖:**
- ✅ `patra-registry-domain`(实现领域端口)
- ✅ `patra-common`(共享工具)
- ✅ `patra-spring-boot-starter-core`(核心 Spring 配置)
- ✅ `patra-spring-boot-starter-mybatis`(MyBatis-Plus 配置)
- ✅ MapStruct(DO↔Domain 转换)

**关键规则:**
- ❌ 切勿在本模块外暴露 DO 对象
- ✅ 始终使用 MapStruct 转换器进行 DO↔Domain 映射
- ✅ 对 JSON 列使用 JsonNode,在领域中转换为 String
- ✅ 将 SQL 保留在 XML 映射器文件中,而非注解

## 包结构

```
com.patra.registry.infra.persistence
├── repository/              # 仓储实现(*RepositoryMpImpl)
├── converter/               # MapStruct 转换器(DO↔Domain)
├── entity/                  # 数据库对象(DO)
│   ├── provenance/         # 数据源配置表
│   ├── expr/               # 表达式元数据表
│   └── dictionary/         # 系统字典表
└── mapper/                  # MyBatis 映射器(接口 + XML)
    ├── provenance/
    ├── expr/
    └── dictionary/
```

## DO 实体约定

所有 DO 实体遵循以下模式:

**命名:** 注册表表用 `Reg*DO`,映射到数据库表名
**基类:** 扩展 `BaseDO`(提供 id、created_at、updated_at、deleted)
**注解:**
- `@TableName("table_name")` - 数据库表映射
- `@TableField("column_name")` - 列映射
- `@Data` / `@SuperBuilder` - Lombok 用于 getters/setters/builders

**示例:**
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

## 映射器约定

**接口命名:** `Reg*Mapper extends BaseMapper<DO>`
**XML 位置:** `src/main/resources/mapper/Reg*Mapper.xml`
**方法命名:** `select*`、`count*`、`insert*`、`update*`、`delete*`

**可重用 SQL 片段:**
所有数据源配置映射器使用共享 SQL 片段:
- `<sql id="activeConfigFilter">` - 时态切片和活动状态过滤
- `<sql id="operationPrecedenceOrder">` - 操作特定优先级排序

**示例:**
```xml
<select id="selectActiveMerged" resultType="...DO">
    SELECT * FROM table_name
    <include refid="activeConfigFilter"/>
    <include refid="operationPrecedenceOrder"/>
    LIMIT 1
</select>
```

## MapStruct 转换器

**命名:** `*EntityConverter` 接口,使用 `@Mapper(componentModel = "spring")` 注解
**目的:** 转换 DO↔Domain 对象,切勿在 infra 层外暴露 DO

**关键模式:**
- 布尔字段: 使用表达式将 SQL `TINYINT(1)` 映射到 Java `Boolean`
- JSON 字段: 使用辅助方法将 `JsonNode` 转换为 `String`
- 字段重命名: 使用 `@Mapping` 注解

**示例:**
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

## 仓储实现

**命名:** `*RepositoryMpImpl implements *Port`
**注解:** `@Repository` + `@RequiredArgsConstructor` + `@Slf4j`

**核心职责:**
1. 注入所需的映射器和转换器
2. 将数据库操作委托给 MyBatis 映射器
3. 使用转换器将 DO 转换为领域对象
4. 添加适当的日志以便调试

**示例:**
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

## 测试策略

- **单元测试:** 测试 MapStruct 转换器(DO↔Domain 映射正确性)
- **集成测试:** 位于 `patra-registry-boot` 模块,具有完整 Spring 上下文
- **TestContainers:** 用于数据库集成测试

## 代码质量标准

本模块遵循严格的重构标准:

✅ **符合 Google Java 代码风格指南**
✅ **所有公共方法都有 JavaDoc**
✅ **所有方法 < 30 行**
✅ **无重复代码(DRY 原则)**
✅ **跨映射器重用 SQL 片段**
✅ **DO 对象切勿泄漏到本模块外**
✅ **所有 DO↔Domain 转换使用 MapStruct**

## 近期重构(2025-10)

**SQL 去重:**
- 将常见查询模式提取到可重用的 `<sql>` 片段
- 应用于所有数据源配置映射器
- 在保持可读性的同时减少重复

**代码简化:**
- 重构 `countNonNullConfigs` 以使用 Stream API
- 提高可读性并减少行数

**JavaDoc 增强:**
- 为所有映射器添加全面的 JavaDoc
- 记录 SQL 片段的目的和用法

## 数据库模式

**数据源配置表:**
- `reg_provenance` - 根数据源记录
- `reg_prov_window_offset_cfg` - 窗口和偏移配置
- `reg_prov_pagination_cfg` - 分页策略
- `reg_prov_http_cfg` - HTTP 策略和超时
- `reg_prov_batching_cfg` - 批处理规则
- `reg_prov_retry_cfg` - 重试和熔断器配置
- `reg_prov_rate_limit_cfg` - 并发和速率限制

**表达式元数据表:**
- `reg_expr_field_dict` - 规范字段定义
- `reg_prov_expr_capability` - 每个数据源的字段能力
- `reg_prov_expr_render_rule` - 表达式渲染规则
- `reg_prov_api_param_map` - API 参数映射

**字典表:**
- `sys_dict_type` - 字典类型定义
- `sys_dict_item` - 字典项
- `sys_dict_item_alias` - 外部系统别名

## 构建命令

```bash
# 仅编译本模块
mvn -q compile -pl :patra-registry-infra

# 运行单元测试(如有)
mvn test -pl :patra-registry-infra

# 与父级完整构建
mvn clean install -pl :patra-registry-infra
```

## 依赖

有关完整的依赖列表,请参见 `pom.xml`。关键依赖:
- MyBatis-Plus(数据库访问)
- MapStruct(对象映射)
- Jackson(使用 JsonNode 处理 JSON)
- Lombok(减少样板代码)
