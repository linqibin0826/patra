# 阶段 4：领域模型自动生成指南

> **生成说明**：此阶段由 Claude 根据阶段 3 的 SQL DDL 自动生成符合六边形架构 + DDD 的领域模型

---

## 🎯 生成目标

根据 SQL DDL，生成：
1. **Domain 层代码**：聚合根、值对象、枚举、仓储接口
2. **Infra 层代码**：Entity、Dao、JpaMapper、Repository 实现
3. **架构分层图**：展示依赖关系
4. **代码结构说明**：包结构和文件组织

---

## 📋 生成步骤

### 步骤 1：聚合识别

从 SQL DDL 中识别聚合根：

**聚合根判断标准**：
1. 有独立的生命周期（可独立创建、查询、删除）
2. 包含完整的审计字段（version, created_at, updated_at, deleted）
3. 不依赖其他表的存在（非纯关联表）

**示例**：
- ✅ `publication` → Publication 聚合根
- ✅ `author` → Author 聚合根
- ⚠️ `publication_author` → 关联关系，可能是独立聚合根或值对象

### 步骤 2：值对象提取

识别应该封装为值对象的字段：

**值对象判断标准**：
1. 多个字段共同表达一个概念（如 PMID + DOI = 标识符）
2. 不可变（Immutable）
3. 可复用

**示例**：
- `pmid` + `doi` → `PublicationIdentifier`（出版物标识符）
- `orcid` → `Orcid`（ORCID 标识符）
- `author_order` + `is_first_author` + `is_corresponding` → `AuthorRole`（作者角色）

### 步骤 3：枚举识别

识别应该定义为枚举的字段：

**枚举判断标准**：
1. 有限的可选值（如状态、类型、语言）
2. 业务含义明确
3. 不频繁变化

**示例**：
- `publication_type` → `PublicationType` 枚举
- `language` → `Language` 枚举

### 步骤 4：生成 Domain 层代码

使用 `resources/templates/domain-model-template.md` 模板生成。

#### 4.1 聚合根代码生成

**命名规则**：
- 文件路径：`domain/model/aggregate/{EntityName}.java`
- 类名：SQL 表名转换为 PascalCase（如 `publication` → `Publication`）

**必需元素**：
```java
@Getter
@ToString
public class {EntityName} {
    // 主键
    private Long id;

    // 业务字段（使用值对象、枚举）
    private {ValueObject} {fieldName};
    private {Enum} {fieldName};

    // 审计字段（匹配 BaseJpaEntity）
    private JsonNode recordRemarks;
    private Long version;
    @Getter(AccessLevel.NONE)
    private byte[] ipAddress;
    private Instant createdAt;
    private Long createdBy;
    private String createdByName;
    private Instant updatedAt;
    private Long updatedBy;
    private String updatedByName;
    private Boolean deleted;

    // 私有构造函数
    private {EntityName}() {}

    // 工厂方法：创建新实例
    public static {EntityName} create(...) {}

    // 工厂方法：从持久化恢复
    public static {EntityName} restore(...) {}

    // 领域行为
    public void businessMethod() {}

    // Package-private 方法（供 Repository 使用）
    void assignId(Long id) {}
    void updateVersion(Long version) {}
}
```

**字段类型映射**：
| SQL 类型 | Java 类型 |
|---------|----------|
| BIGINT UNSIGNED | Long |
| VARCHAR | String |
| TEXT | String |
| DATE | LocalDate |
| TIMESTAMP(6) | Instant |
| DECIMAL | BigDecimal |
| INT UNSIGNED | Integer |
| TINYINT(1) | Boolean |
| JSON | JsonNode |
| VARBINARY(16) | byte[] |

#### 4.2 值对象代码生成

**命名规则**：
- 文件路径：`domain/model/vo/{ValueObjectName}.java`
- 类名：业务含义（如 `PublicationIdentifier`）

**必需元素**：
```java
@Getter
@ToString
@EqualsAndHashCode
public class {ValueObjectName} {
    private final {Type} {field1};  // final = 不可变
    private final {Type} {field2};

    private {ValueObjectName}({Type} {field1}, {Type} {field2}) {
        this.{field1} = {field1};
        this.{field2} = {field2};
    }

    // 静态工厂方法
    public static {ValueObjectName} of({Type} {field1}, {Type} {field2}) {
        // 验证逻辑
        return new {ValueObjectName}({field1}, {field2});
    }

    // 业务方法
    public boolean hasXxx() {}
}
```

#### 4.3 枚举代码生成

**命名规则**：
- 文件路径：`domain/model/enums/{EnumName}.java`
- 枚举名：字段名转换为 PascalCase（如 `publication_type` → `PublicationType`）

**必需元素**：
```java
@Getter
public enum {EnumName} {
    VALUE1("code1", "描述1"),
    VALUE2("code2", "描述2");

    private final String code;
    private final String description;

    {EnumName}(String code, String description) {
        this.code = code;
        this.description = description;
    }

    // 从代码获取枚举
    public static {EnumName} fromCode(String code) {
        if (code == null) return null;
        for ({EnumName} e : values()) {
            if (e.code.equals(code)) return e;
        }
        throw new IllegalArgumentException("未知的{枚举类型}: " + code);
    }
}
```

#### 4.4 仓储接口代码生成

**命名规则**：
- 文件路径：`domain/port/{EntityName}Repository.java`
- 接口名：`{EntityName}Repository`

**必需元素**：
```java
public interface {EntityName}Repository {
    // 保存
    {EntityName} save({EntityName} entity);
    List<{EntityName}> saveBatch(List<{EntityName}> entities);

    // 查询
    Optional<{EntityName}> findById(Long id);
    Optional<{EntityName}> findByUniqueKey(String key);
    List<{EntityName}> findByCondition(...);

    // 删除
    void deleteById(Long id);
}
```

### 步骤 5：生成 Infra 层代码

#### 5.1 JPA Entity 代码生成

**命名规则**：
- 文件路径：`infra/persistence/entity/{EntityName}Entity.java`
- 类名：`{EntityName}Entity`

**必需元素**：
```java
@Entity
@Table(name = "{table_name}")
@Getter
@Setter
public class {EntityName}Entity extends BaseJpaEntity {

    @Column(name = "{column_name}")
    private {Type} {fieldName};

    // 枚举存储为 String
    @Column(name = "{enum_column}")
    private String {enumField};

    // JSON 字段
    @Type(JsonType.class)
    @Column(name = "{json_column}", columnDefinition = "json")
    private {JsonJavaType} {jsonField};
}
```

**字段映射**：
- SQL 列名（snake_case）→ Java 字段名（camelCase）
- 使用 `@Column` 明确映射

#### 5.2 JpaMapper 代码生成（MapStruct）

**命名规则**：
- 文件路径：`infra/persistence/mapper/{EntityName}JpaMapper.java`
- 接口名：`{EntityName}JpaMapper`

**必需元素**：
```java
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface {EntityName}JpaMapper {

    // 聚合根 → JPA Entity
    @Mapping(target = "{targetField}", source = "{sourceExpression}")
    {EntityName}Entity toEntity({EntityName} aggregate);

    // JPA Entity → 聚合根
    default {EntityName} toDomain({EntityName}Entity entity) {
        if (entity == null) return null;

        // String → 枚举: Enum.fromCode(entity.getXxx())
        // 基本类型 → 值对象

        // 使用 restore 工厂方法
        return {EntityName}.restore(...);
    }

    // 枚举转换辅助方法
    default String enumToCode({EnumType} value) {
        return value != null ? value.getCode() : null;
    }

    default {EnumType} codeToEnum(String code) {
        return code != null ? {EnumType}.fromCode(code) : null;
    }
}
```

#### 5.3 Dao 代码生成（Spring Data JPA）

**命名规则**：
- 文件路径：`infra/persistence/dao/{EntityName}Dao.java`
- 接口名：`{EntityName}Dao`

**必需元素**：
```java
public interface {EntityName}Dao extends JpaRepository<{EntityName}Entity, Long> {

    // 简单查询使用方法命名约定
    Optional<{EntityName}Entity> findByUniqueKey(String key);

    // 复杂查询使用 @Query + JPQL
    @Query("SELECT e FROM {EntityName}Entity e WHERE e.status = :status")
    List<{EntityName}Entity> findByStatus(@Param("status") String status);

    // JpaRepository 已提供基础 CRUD：
    // - save(): 保存或更新
    // - findById(): 按 ID 查询
    // - saveAll(): 批量保存
    // - deleteById(): 按 ID 删除
}
```

#### 5.4 Repository 实现代码生成

**命名规则**：
- 文件路径：`infra/persistence/repository/{EntityName}RepositoryAdapter.java`
- 类名：`{EntityName}RepositoryAdapter`

**必需元素**：
```java
@Repository
@RequiredArgsConstructor
public class {EntityName}RepositoryAdapter implements {EntityName}Repository {

    private final {EntityName}Dao dao;
    private final {EntityName}JpaMapper mapper;

    @Override
    public {EntityName} save({EntityName} aggregate) {
        {EntityName}Entity entity = mapper.toEntity(aggregate);

        // JPA 的 save() 自动处理 insert/update
        // 乐观锁由 @Version 注解自动管理
        {EntityName}Entity saved = dao.save(entity);

        // 回写 ID（新增时）
        if (aggregate.getId() == null) {
            aggregate.assignId(saved.getId());
        }

        // 更新版本号
        aggregate.updateVersion(saved.getVersion());
        return aggregate;
    }

    @Override
    public Optional<{EntityName}> findById(Long id) {
        return dao.findById(id)
            .map(mapper::toDomain);
    }

    // 实现其他方法...
}
```

---

## 📐 生成规范

### 1. 命名规范

| 对象类型 | Domain 层 | Infra 层 |
|---------|----------|----------|
| 聚合根 | `Publication` | `PublicationEntity` |
| 值对象 | `PublicationIdentifier` | - |
| 枚举 | `PublicationType` | - |
| 仓储接口 | `PublicationRepository` | - |
| 仓储实现 | - | `PublicationRepositoryAdapter` |
| JPA Dao | - | `PublicationDao` |
| MapStruct 转换器 | - | `PublicationJpaMapper` |

### 2. 包结构规范

**Domain 层**：
```
{module}-domain/
└── src/main/java/com/patra/{module}/domain/
    ├── model/
    │   ├── aggregate/    # 聚合根
    │   ├── vo/           # 值对象
    │   └── enums/        # 枚举
    └── port/             # 仓储接口（端口）
```

**Infra 层**：
```
{module}-infra/
└── src/main/java/com/patra/{module}/infra/
    └── persistence/
        ├── entity/       # JPA 实体
        ├── dao/          # Spring Data JPA Dao
        ├── mapper/       # MapStruct 转换器
        └── repository/   # 仓储实现（适配器）
```

### 3. 依赖方向规范

```
Domain 层（纯 Java，无框架依赖）
    ↑
    ↑ 依赖倒置
    ↑
Infra 层（依赖 Domain 层，实现端口接口）
```

- ✅ Infra 层可以导入 Domain 层的类
- ❌ Domain 层不能导入 Infra 层的类
- ❌ Domain 层不能有任何框架注解（Spring、JPA 等）

---

## ⚠️ 注意事项

### 强制规范
- ✅ **时间类型**：使用 `Instant`（不是 `LocalDateTime`）
- ✅ **Entity 命名**：使用 `Entity` 后缀（不是 `DO` 或 `PO`）
- ✅ **BaseJpaEntity 继承**：所有 JPA 实体必须继承 `BaseJpaEntity`
- ✅ **转换器方法**：使用 `toEntity()` 和 `toDomain()`
- ✅ **工厂方法**：使用 `create()` 和 `restore()`

### 设计原则
- ✅ **聚合根不变量**：在构造函数/工厂方法中验证
- ✅ **值对象不可变**：所有字段 `final`
- ✅ **枚举从代码恢复**：提供 `fromCode()` 方法
- ✅ **仓储操作聚合**：不暴露 Entity 给外层

---

## 📝 生成输出格式

生成的领域模型应保存为独立文件，建议命名：
- 项目内：`resources/stages/stage-4-domain-model.md`
- 示例：`resources/examples/{project-name}/4-domain-model.md`

文件结构：
```markdown
# 阶段 4：领域模型映射 - {项目名称}

## 🏗️ 架构分层
...

## 📦 聚合识别
...

## 📂 Domain 层代码结构
...

## 💻 Domain 层代码生成
### 1. 聚合根：{EntityName}.java
### 2. 值对象：{ValueObjectName}.java
### 3. 枚举：{EnumName}.java
### 4. 仓储接口：{EntityName}Repository.java

## 📂 Infra 层代码结构
...

## 💻 Infra 层代码生成
### 1. Entity：{EntityName}Entity.java
### 2. Dao：{EntityName}Dao.java
### 3. JpaMapper：{EntityName}JpaMapper.java
### 4. Repository 实现：{EntityName}RepositoryAdapter.java

## ✅ 设计验证
...
```

---

## 🔗 相关资源

- **模板文件**：[domain-model-template.md](../templates/domain-model-template.md)
- **实际代码参考**：
  - FileMetadata.java（聚合根示例）
  - FileMetadataEntity.java（JPA 实体示例）
  - BaseJpaEntity.java（审计字段基类）

---

## 下一步

领域模型生成完成后，可选：
- **[阶段 5：设计决策记录](stage-5-decisions.md)** - 记录关键设计决策
- **代码实现** - 将生成的代码集成到实际项目中
