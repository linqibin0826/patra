# 领域模型映射（Patra 项目标准）

基于数据库设计自动生成的 DDD 领域模型，遵循 Patra 项目的六边形架构规范。

## 📦 聚合根识别

{{#each aggregates}}
### {{aggregate_name}} 聚合

**聚合根：** `{{root_entity}}`
**边界：** {{boundary_description}}

**包含的实体：**
{{#each entities}}
- {{entity_name}} - {{entity_role}}
{{/each}}

**值对象：**
{{#each value_objects}}
- {{vo_name}} - {{vo_description}}
{{/each}}

---
{{/each}}

## 🏗️ Patra 项目标准层次结构

```
patra-{{module}}/
├── patra-{{module}}-domain/                    # 领域层（纯 Java）
│   └── src/main/java/com/patra/{{module}}/domain/
│       ├── model/
│       │   ├── aggregate/                      # 聚合根
│       │   │   └── {{EntityName}}.java
│       │   ├── vo/                             # 值对象
│       │   │   └── {{ValueObject}}.java
│       │   └── enums/                          # 枚举类型
│       │       └── {{EnumName}}.java
│       └── port/                               # 端口（仓储接口）
│           └── {{EntityName}}Repository.java
│
└── patra-{{module}}-infra/                     # 基础设施层
    └── src/main/java/com/patra/{{module}}/infra/
        └── persistence/
            ├── entity/                         # JPA 实体
            │   └── {{EntityName}}Entity.java
            ├── dao/                            # Spring Data JPA Dao
            │   └── {{EntityName}}Dao.java
            ├── mapper/                         # MapStruct 转换器
            │   └── {{EntityName}}JpaMapper.java
            └── adapter/persistence/            # 仓储适配器
                └── {{EntityName}}RepositoryAdapter.java
```

---

## 📋 Domain 层：聚合根（纯 Java，使用 Lombok）

```java
package com.patra.{{module}}.domain.model.aggregate;

import com.patra.{{module}}.domain.model.enums.*;
import com.patra.{{module}}.domain.model.vo.*;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;

/// {{entity_description}}聚合根。
/// 
/// {{aggregate_responsibility_description}}
/// 
/// 聚合根通过工厂方法创建新实例({@link #create})或从持久化快照恢复({@link #restore}),
/// 确保所有必需的业务规则在对象创建时得到验证。
@Getter
@ToString
public class {{EntityName}} {

  // =========================================
  // 实体标识
  // =========================================
  /// 主键标识符
  private Long id;

  // =========================================
  // 业务字段
  // =========================================
  {{#each business_fields}}
  /// {{field_comment}}
  private {{java_type}} {{field_name}};

  {{/each}}

  // =========================================
  // 值对象
  // =========================================
  {{#each value_objects}}
  /// {{vo_comment}}
  private {{vo_type}} {{vo_field_name}};

  {{/each}}

  // =========================================
  // 枚举字段
  // =========================================
  {{#each enum_fields}}
  /// {{enum_comment}}
  private {{enum_type}} {{enum_field_name}};

  {{/each}}

  // =========================================
  // 审计字段（标准化）
  // =========================================
  /// 记录备注(JSON格式)
  private String recordRemarks;

  /// 乐观锁版本号
  private Long version;

  /// 请求者IP地址(二进制格式)
  @Getter(AccessLevel.NONE)
  private byte[] ipAddress;

  /// 创建时间
  private Instant createdAt;

  /// 创建人ID
  private Long createdBy;

  /// 创建人姓名
  private String createdByName;

  /// 更新时间
  private Instant updatedAt;

  /// 更新人ID
  private Long updatedBy;

  /// 更新人姓名
  private String updatedByName;

  /// 软删除标志
  private Boolean deleted;

  // =========================================
  // 私有构造函数
  // =========================================
  private {{EntityName}}() {
    // 使用静态工厂方法创建实例
  }

  // =========================================
  // 工厂方法
  // =========================================

  /// 创建新的{{entity_display_name}}聚合根,初始化必需的审计属性。
/// 
/// 工厂方法用于创建新的记录,自动设置初始状态、版本号和审计时间戳。
/// 
/// @param {{factory_params_doc}}
/// @return 新创建的聚合根实例
  public static {{EntityName}} create({{factory_params}}) {
    {{EntityName}} entity = new {{EntityName}}();

    // 设置业务字段
    {{#each factory_assignments}}
    entity.{{field_name}} = {{field_name}};
    {{/each}}

    // 初始化审计字段
    Instant now = Instant.now();
    entity.version = 0L;
    entity.createdAt = now;
    entity.updatedAt = now;
    entity.deleted = false;

    // 验证业务规则
    entity.validate();

    return entity;
  }

  /// 从持久化快照恢复聚合根。
/// 
/// 用于仓储实现从数据库重建聚合根实例,绕过业务规则验证。
/// 
/// @param id 主键ID
/// @param {{restore_params}}
/// @return 恢复的聚合根实例
  public static {{EntityName}} restore(
      Long id,
      {{restore_params}}) {
    {{EntityName}} entity = new {{EntityName}}();
    entity.id = id;

    // 恢复业务字段
    {{#each restore_assignments}}
    entity.{{field_name}} = {{field_name}};
    {{/each}}

    // 恢复审计字段
    entity.recordRemarks = recordRemarks;
    entity.version = version;
    entity.ipAddress = ipAddress;
    entity.createdAt = createdAt;
    entity.createdBy = createdBy;
    entity.createdByName = createdByName;
    entity.updatedAt = updatedAt;
    entity.updatedBy = updatedBy;
    entity.updatedByName = updatedByName;
    entity.deleted = deleted;

    return entity;
  }

  // =========================================
  // 领域行为
  // =========================================

  {{#each domain_behaviors}}
  /// {{behavior_description}}
/// 
/// @param {{behavior_params_doc}}
///    {{#if behavior_throws}}
/// @throws {{behavior_throws}}
///    {{/if}}
  public {{behavior_return_type}} {{behavior_name}}({{behavior_params}}) {
    // 前置条件检查
    {{#each preconditions}}
    if ({{condition}}) {
      throw new IllegalStateException("{{error_message}}");
    }
    {{/each}}

    // 业务逻辑
    {{behavior_implementation}}

    // 后置验证
    this.validate();

    {{#if has_return}}
    return {{return_value}};
    {{/if}}
  }

  {{/each}}

  // =========================================
  // 业务规则验证
  // =========================================

  /// 验证实体状态的有效性。
/// 
/// @throws IllegalStateException 如果状态不合法
  private void validate() {
    {{#each validations}}
    if ({{validation_condition}}) {
      throw new IllegalStateException("{{error_message}}");
    }
    {{/each}}

    // 验证值对象
    {{#each value_objects}}
    if ({{vo_field_name}} != null) {
      // 值对象内部已有验证逻辑
    }
    {{/each}}
  }

  // =========================================
  // 包级可见的ID分配方法（仓储使用）
  // =========================================

  /// 分配主键ID（仅供仓储实现调用）。
/// 
/// @param id 数据库生成的主键
  void assignId(Long id) {
    this.id = id;
  }

  /// 更新版本号（仅供仓储实现调用）。
/// 
/// @param version 新的版本号
  void updateVersion(Long version) {
    this.version = version;
  }

  // =========================================
  // 对象相等性
  // =========================================

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    {{EntityName}} that = ({{EntityName}}) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
```

---

## 📦 Domain 层：值对象（不可变）

{{#each value_objects}}
```java
package com.patra.{{module}}.domain.model.vo;

import java.util.Objects;
import lombok.Getter;
import lombok.ToString;

/// {{vo_description}}值对象。
/// 
/// **值对象特征**
/// 
/// - 不可变（Immutable）
///   - 无标识，通过属性判断相等
///   - 封装业务规则和验证逻辑
/// 
@Getter
@ToString
public final class {{vo_class_name}} {

  {{#each fields}}
  /// {{field_comment}}
  private final {{java_type}} {{field_name}};

  {{/each}}

  /// 私有构造函数。
  private {{vo_class_name}}({{vo_constructor_params}}) {
    {{#each fields}}
    this.{{field_name}} = {{field_name}};
    {{/each}}
    this.validate();
  }

  /// 创建值对象实例（工厂方法）。
/// 
/// @param {{vo_factory_params_doc}}
/// @return 值对象实例
/// @throws IllegalArgumentException 如果参数不合法
  public static {{vo_class_name}} of({{vo_factory_params}}) {
    return new {{vo_class_name}}({{vo_factory_args}});
  }

  /// 验证值对象的有效性。
/// 
/// @throws IllegalArgumentException 如果值不合法
  private void validate() {
    {{#each validations}}
    if ({{validation_condition}}) {
      throw new IllegalArgumentException("{{error_message}}");
    }
    {{/each}}
  }

  // 对象相等性（基于所有字段）
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    {{vo_class_name}} that = ({{vo_class_name}}) o;
    return {{equality_conditions}};
  }

  @Override
  public int hashCode() {
    return Objects.hash({{hash_fields}});
  }
}
```
{{/each}}

---

## 🎯 Domain 层：枚举类型

{{#each enum_types}}
```java
package com.patra.{{module}}.domain.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/// {{enum_description}}枚举。
@Getter
@RequiredArgsConstructor
public enum {{enum_class_name}} {

  {{#each enum_values}}
  /// {{value_comment}}
  {{value_name}}("{{value_code}}", "{{value_display}}"){{#unless @last}},{{/unless}}
  {{/each}};

  /// 数据库存储的代码值
  private final String code;

  /// 显示名称
  private final String displayName;

  /// 从代码值解析枚举。
/// 
/// @param code 数据库存储的代码值
/// @return 对应的枚举值
/// @throws IllegalArgumentException 如果代码值无效
  public static {{enum_class_name}} fromCode(String code) {
    for ({{enum_class_name}} value : values()) {
      if (value.code.equals(code)) {
        return value;
      }
    }
    throw new IllegalArgumentException("未知的{{enum_description}}代码: " + code);
  }
}
```
{{/each}}

---

## 🔌 Domain 层：仓储接口（端口）

```java
package com.patra.{{module}}.domain.port;

import com.patra.{{module}}.domain.model.aggregate.{{EntityName}};
import java.util.Optional;

/// {{entity_description}}仓储抽象。
/// 
/// 定义持久化 {@link {{EntityName}}} 聚合根的仓储接口,作为领域层的端口,
/// 由基础设施层的仓储实现提供具体的持久化逻辑。
/// 
/// 遵循端口-适配器模式,领域层通过此接口与持久化机制解耦,
/// 基础设施层负责实现数据库访问、ORM映射和事务管理。
public interface {{EntityName}}Repository {

  /// 持久化提供的聚合根。
/// 
/// 保存新的或更新现有的记录。实现需要处理:
/// 
/// - 为新记录生成主键ID并通过 {@link {{EntityName}}#assignId} 回填
///   - 通过 {@link {{EntityName}}#updateVersion} 更新乐观锁版本号
///   - 确保唯一约束不被违反
/// 
/// @param entity 要存储的聚合根
/// @return 持久化后的聚合根(已填充标识符和版本号)
  {{EntityName}} save({{EntityName}} entity);

  {{#if has_business_key}}
  /// 通过{{business_key_description}}加载聚合根。
/// 
/// @param {{business_key_param}} {{business_key_param_comment}}
/// @return 存在时返回聚合根,否则返回空
  Optional<{{EntityName}}> findBy{{business_key_name}}({{business_key_type}} {{business_key_param}});
  {{/if}}

  /// 通过主键ID加载聚合根。
/// 
/// @param id 主键ID
/// @return 存在时返回聚合根,否则返回空
  Optional<{{EntityName}}> findById(Long id);
}
```

---

## 🛠️ Infra 层：JPA 实体（继承 BaseJpaEntity）

```java
package com.patra.{{module}}.infra.persistence.entity;

import com.patra.starter.jpa.entity.BaseJpaEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

/// {{entity_description}} JPA 实体。
///
/// 表示 `{{table_name}}` 表的 JPA 实体,用于 Spring Data JPA 的 ORM 映射。
/// 继承自 {@link BaseJpaEntity},包含标准的审计字段(id、version、创建人、更新人、软删除标志等)。
///
/// JPA 实体是持久化层的一部分,负责数据库表结构与 Java 对象之间的映射,
/// 通过 MapStruct 转换器(`{{EntityName}}JpaMapper`)与领域聚合根进行转换。
@Entity
@Table(name = "{{table_name}}")
@Getter
@Setter
public class {{EntityName}}Entity extends BaseJpaEntity {

  {{#each business_fields_do}}
  /// {{field_comment}}
  @Column(name = "{{column_name}}")
  private {{java_type}} {{field_name}};

  {{/each}}

  {{#each json_fields}}
  /// {{field_comment}}
  @Type(JsonType.class)
  @Column(name = "{{column_name}}", columnDefinition = "json")
  private {{json_java_type}} {{field_name}};

  {{/each}}

  {{#each enum_fields}}
  /// {{field_comment}}
  @Column(name = "{{column_name}}")
  private String {{field_name}};

  {{/each}}

  {{#each timestamp_fields}}
  /// {{field_comment}}
  @Column(name = "{{column_name}}")
  private Instant {{field_name}};

  {{/each}}
}
```

**注意**：`BaseJpaEntity` 已包含以下字段，无需重复定义：
```java
- id: BIGINT UNSIGNED (雪花 ID 主键)
- recordRemarks: JSON (备注)
- version: BIGINT UNSIGNED (乐观锁 @Version)
- ipAddress: VARBINARY(16) (IP地址)
- createdAt: TIMESTAMP(6) (创建时间 @CreatedDate)
- createdBy: BIGINT UNSIGNED (创建人ID @CreatedBy)
- createdByName: VARCHAR(100) (创建人姓名)
- updatedAt: TIMESTAMP(6) (更新时间 @LastModifiedDate)
- updatedBy: BIGINT UNSIGNED (更新人ID @LastModifiedBy)
- updatedByName: VARCHAR(100) (更新人姓名)
- deleted: TINYINT(1) (软删除 @SoftDelete)
```

---

## 🔧 Infra 层：JPA Dao 接口

```java
package com.patra.{{module}}.infra.persistence.dao;

import com.patra.{{module}}.infra.persistence.entity.{{EntityName}}Entity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/// {{entity_description}} JPA Dao。
///
/// 继承 JpaRepository，获得标准 CRUD 和分页查询能力。
/// 简单查询使用方法命名约定，复杂查询使用 @Query + JPQL。
public interface {{EntityName}}Dao extends JpaRepository<{{EntityName}}Entity, Long> {

  {{#if has_business_key}}
  /// 通过{{business_key_description}}查询。
  ///
  /// @param {{business_key_param}} {{business_key_param_comment}}
  /// @return 存在时返回实体，否则返回空
  Optional<{{EntityName}}Entity> findBy{{business_key_name}}({{business_key_type}} {{business_key_param}});
  {{/if}}

  // 继承 JpaRepository 的 CRUD 方法：
  // - save(entity): 保存或更新
  // - findById(id): 按 ID 查询
  // - findAll(): 查询全部
  // - deleteById(id): 按 ID 删除
  // - saveAll(entities): 批量保存
}
```

---

## 🔄 Infra 层：MapStruct 转换器（聚合根 ↔ Entity）

```java
package com.patra.{{module}}.infra.persistence.mapper;

import com.patra.{{module}}.domain.model.aggregate.{{EntityName}};
import com.patra.{{module}}.domain.model.enums.*;
import com.patra.{{module}}.domain.model.vo.*;
import com.patra.{{module}}.infra.persistence.entity.{{EntityName}}Entity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/// {{EntityName}} 聚合根与 JPA 实体之间的 MapStruct 转换器。
///
/// 使用 MapStruct 自动生成类型安全的对象映射代码。
/// 编译时生成实现类，运行时零反射开销。
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface {{EntityName}}JpaMapper {

  /// 聚合根 → JPA 实体。
  ///
  /// @param aggregate 聚合根
  /// @return JPA 实体
  {{#each entity_mappings}}
  @Mapping(target = "{{target_field}}", source = "{{source_expression}}")
  {{/each}}
  {{EntityName}}Entity toEntity({{EntityName}} aggregate);

  /// JPA 实体 → 聚合根。
  ///
  /// 使用 {@link {{EntityName}}#restore} 工厂方法重建聚合根。
  ///
  /// @param entity JPA 实体
  /// @return 聚合根
  default {{EntityName}} toDomain({{EntityName}}Entity entity) {
    if (entity == null) {
      return null;
    }

    return {{EntityName}}.restore(
        entity.getId(),
        {{#each restore_args}}
        {{arg_expression}}{{#unless @last}},{{/unless}}
        {{/each}},
        // 审计字段
        entity.getRecordRemarks(),
        entity.getVersion(),
        entity.getIpAddress(),
        entity.getCreatedAt(),
        entity.getCreatedBy(),
        entity.getCreatedByName(),
        entity.getUpdatedAt(),
        entity.getUpdatedBy(),
        entity.getUpdatedByName(),
        entity.getDeleted()
    );
  }

  // =========================================
  // 枚举转换辅助方法
  // =========================================

  {{#each enum_types}}
  /// 枚举 → 数据库字符串代码。
  default String {{enum_field_name}}ToCode({{enum_class_name}} value) {
    return value != null ? value.getCode() : null;
  }

  /// 数据库字符串代码 → 枚举。
  default {{enum_class_name}} codeTo{{enum_class_name}}(String code) {
    return code != null ? {{enum_class_name}}.fromCode(code) : null;
  }
  {{/each}}

  // =========================================
  // 值对象转换辅助方法
  // =========================================

  {{#each value_objects}}
  /// 值对象 → 基本类型（根据实际需要选择性实现）。
  default {{vo_primitive_type}} {{vo_field_name}}ToValue({{vo_class_name}} vo) {
    return vo != null ? vo.{{vo_getter}}() : null;
  }

  /// 基本类型 → 值对象。
  default {{vo_class_name}} valueTo{{vo_class_name}}({{vo_primitive_type}} value) {
    return value != null ? {{vo_class_name}}.of(value) : null;
  }
  {{/each}}
}
```

---

## 🔌 Infra 层：仓储实现

```java
package com.patra.{{module}}.infra.persistence.repository;

import com.patra.{{module}}.domain.model.aggregate.{{EntityName}};
import com.patra.{{module}}.domain.port.{{EntityName}}Repository;
import com.patra.{{module}}.infra.persistence.dao.{{EntityName}}Dao;
import com.patra.{{module}}.infra.persistence.entity.{{EntityName}}Entity;
import com.patra.{{module}}.infra.persistence.mapper.{{EntityName}}JpaMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/// {{EntityName}} 仓储适配器。
///
/// 适配器模式：将 Spring Data JPA 适配到领域层的仓储接口。
/// 使用 Dao 进行数据访问，使用 JpaMapper 进行对象转换。
@Repository
@RequiredArgsConstructor
public class {{EntityName}}RepositoryAdapter implements {{EntityName}}Repository {

  private final {{EntityName}}Dao dao;
  private final {{EntityName}}JpaMapper mapper;

  @Override
  public {{EntityName}} save({{EntityName}} aggregate) {
    {{EntityName}}Entity entity = mapper.toEntity(aggregate);

    // JPA 的 save() 方法自动处理 insert/update
    // 乐观锁由 @Version 注解自动管理
    {{EntityName}}Entity saved = dao.save(entity);

    // 回写 ID（新增时）
    if (aggregate.getId() == null) {
      aggregate.assignId(saved.getId());
    }

    // 更新版本号
    aggregate.updateVersion(saved.getVersion());

    return aggregate;
  }

  {{#if has_business_key}}
  @Override
  public Optional<{{EntityName}}> findBy{{business_key_name}}({{business_key_type}} {{business_key_param}}) {
    return dao.findBy{{business_key_name}}({{business_key_param}})
        .map(mapper::toDomain);
  }
  {{/if}}

  @Override
  public Optional<{{EntityName}}> findById(Long id) {
    return dao.findById(id)
        .map(mapper::toDomain);
  }
}
```

---

## 📚 使用示例

### 在应用层创建聚合

```java
@Service
@RequiredArgsConstructor
public class {{EntityName}}AppService {

  private final {{EntityName}}Repository repository;

  public Long create{{EntityName}}({{create_params}}) {
    // 1. 创建领域对象
    {{EntityName}} entity = {{EntityName}}.create({{create_args}});

    // 2. 保存到仓储
    {{EntityName}} saved = repository.save(entity);

    return saved.getId();
  }
}
```

### 执行领域行为

```java
public void {{behavior_example_name}}(Long id, {{behavior_params}}) {
  // 1. 加载聚合
  {{EntityName}} entity = repository.findById(id)
      .orElseThrow(() -> new EntityNotFoundException("未找到{{entity_display_name}}"));

  // 2. 执行领域方法
  entity.{{behavior_method_call}};

  // 3. 保存变更
  repository.save(entity);
}
```

---

## 🎯 Patra 项目设计规范

### 1. 命名约定

| 层次 | 命名规范 | 示例 |
|------|---------|------|
| 聚合根 | 大驼峰 | `FileMetadata` |
| 值对象 | 大驼峰 | `StorageKey` |
| 枚举 | 大驼峰 | `FileStatus` |
| JPA 实体 | 大驼峰 + Entity 后缀 | `FileMetadataEntity` |
| JPA Dao | 大驼峰 + Dao 后缀 | `FileMetadataDao` |
| MapStruct 转换器 | 大驼峰 + JpaMapper 后缀 | `FileMetadataJpaMapper` |
| 仓储接口 | 大驼峰 + Repository 后缀 | `FileMetadataRepository` |
| 仓储适配器 | 大驼峰 + RepositoryAdapter 后缀 | `FileMetadataRepositoryAdapter` |

### 2. 包结构约定

```
domain/
  ├── model/
  │   ├── aggregate/     # 聚合根和实体
  │   ├── vo/            # 值对象
  │   └── enums/         # 枚举类型
  └── port/              # 端口（仓储接口）

infra/
  └── persistence/
      ├── entity/        # JPA 实体
      ├── dao/           # Spring Data JPA Dao
      ├── mapper/        # MapStruct 转换器
      └── repository/    # 仓储实现（适配器）
```

### 3. 审计字段使用 BaseJpaEntity

所有 JPA 实体都继承 `BaseJpaEntity`，包含标准审计字段（雪花 ID、乐观锁、软删除、创建/更新时间等），无需重复定义。

### 4. 时间类型统一使用 Instant

- Domain 层：`java.time.Instant`
- Infra 层（Entity）：`java.time.Instant`
- 数据库：`TIMESTAMP(6)`

### 5. 枚举存储为字符串代码

- Domain 层：枚举类型（如 `FileStatus`）
- Infra 层（Entity）：`String` 类型
- 数据库：`VARCHAR(32)`
- MapStruct 转换器负责枚举与字符串的转换

---

## 📖 相关资源

- [六边形架构](https://alistair.cockburn.us/hexagonal-architecture/)
- [Spring Data JPA 官方文档](https://spring.io/projects/spring-data-jpa)
- [MapStruct 官方文档](https://mapstruct.org/)
- [Patra 项目开发规范](../../../../CLAUDE.md)