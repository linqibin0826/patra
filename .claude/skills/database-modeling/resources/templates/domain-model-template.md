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
            ├── entity/                         # 数据对象（DO）
            │   └── {{EntityName}}DO.java
            ├── mapper/                         # MyBatis Mapper
            │   └── {{EntityName}}Mapper.java
            ├── converter/                      # 转换器
            │   └── {{EntityName}}Converter.java
            └── repository/                     # 仓储实现
                └── {{EntityName}}RepositoryMpImpl.java
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

/**
 * {{entity_description}}聚合根。
 *
 * <p>{{aggregate_responsibility_description}}
 *
 * <p>聚合根通过工厂方法创建新实例({@link #create})或从持久化快照恢复({@link #restore}),
 * 确保所有必需的业务规则在对象创建时得到验证。
 */
@Getter
@ToString
public class {{EntityName}} {

  // =========================================
  // 实体标识
  // =========================================
  /** 主键标识符 */
  private Long id;

  // =========================================
  // 业务字段
  // =========================================
  {{#each business_fields}}
  /** {{field_comment}} */
  private {{java_type}} {{field_name}};

  {{/each}}

  // =========================================
  // 值对象
  // =========================================
  {{#each value_objects}}
  /** {{vo_comment}} */
  private {{vo_type}} {{vo_field_name}};

  {{/each}}

  // =========================================
  // 枚举字段
  // =========================================
  {{#each enum_fields}}
  /** {{enum_comment}} */
  private {{enum_type}} {{enum_field_name}};

  {{/each}}

  // =========================================
  // 审计字段（标准化）
  // =========================================
  /** 记录备注(JSON格式) */
  private String recordRemarks;

  /** 乐观锁版本号 */
  private Long version;

  /** 请求者IP地址(二进制格式) */
  @Getter(AccessLevel.NONE)
  private byte[] ipAddress;

  /** 创建时间 */
  private Instant createdAt;

  /** 创建人ID */
  private Long createdBy;

  /** 创建人姓名 */
  private String createdByName;

  /** 更新时间 */
  private Instant updatedAt;

  /** 更新人ID */
  private Long updatedBy;

  /** 更新人姓名 */
  private String updatedByName;

  /** 软删除标志 */
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

  /**
   * 创建新的{{entity_display_name}}聚合根,初始化必需的审计属性。
   *
   * <p>工厂方法用于创建新的记录,自动设置初始状态、版本号和审计时间戳。
   *
   * @param {{factory_params_doc}}
   * @return 新创建的聚合根实例
   */
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

  /**
   * 从持久化快照恢复聚合根。
   *
   * <p>用于仓储实现从数据库重建聚合根实例,绕过业务规则验证。
   *
   * @param id 主键ID
   * @param {{restore_params}}
   * @return 恢复的聚合根实例
   */
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
  /**
   * {{behavior_description}}
   *
   * @param {{behavior_params_doc}}
   {{#if behavior_throws}}
   * @throws {{behavior_throws}}
   {{/if}}
   */
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

  /**
   * 验证实体状态的有效性。
   *
   * @throws IllegalStateException 如果状态不合法
   */
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

  /**
   * 分配主键ID（仅供仓储实现调用）。
   *
   * @param id 数据库生成的主键
   */
  void assignId(Long id) {
    this.id = id;
  }

  /**
   * 更新版本号（仅供仓储实现调用）。
   *
   * @param version 新的版本号
   */
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

/**
 * {{vo_description}}值对象。
 *
 * <p><b>值对象特征</b></p>
 * <ul>
 *   <li>不可变（Immutable）</li>
 *   <li>无标识，通过属性判断相等</li>
 *   <li>封装业务规则和验证逻辑</li>
 * </ul>
 */
@Getter
@ToString
public final class {{vo_class_name}} {

  {{#each fields}}
  /** {{field_comment}} */
  private final {{java_type}} {{field_name}};

  {{/each}}

  /**
   * 私有构造函数。
   */
  private {{vo_class_name}}({{vo_constructor_params}}) {
    {{#each fields}}
    this.{{field_name}} = {{field_name}};
    {{/each}}
    this.validate();
  }

  /**
   * 创建值对象实例（工厂方法）。
   *
   * @param {{vo_factory_params_doc}}
   * @return 值对象实例
   * @throws IllegalArgumentException 如果参数不合法
   */
  public static {{vo_class_name}} of({{vo_factory_params}}) {
    return new {{vo_class_name}}({{vo_factory_args}});
  }

  /**
   * 验证值对象的有效性。
   *
   * @throws IllegalArgumentException 如果值不合法
   */
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

/**
 * {{enum_description}}枚举。
 */
@Getter
@RequiredArgsConstructor
public enum {{enum_class_name}} {

  {{#each enum_values}}
  /** {{value_comment}} */
  {{value_name}}("{{value_code}}", "{{value_display}}"){{#unless @last}},{{/unless}}
  {{/each}};

  /** 数据库存储的代码值 */
  private final String code;

  /** 显示名称 */
  private final String displayName;

  /**
   * 从代码值解析枚举。
   *
   * @param code 数据库存储的代码值
   * @return 对应的枚举值
   * @throws IllegalArgumentException 如果代码值无效
   */
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

/**
 * {{entity_description}}仓储抽象。
 *
 * <p>定义持久化 {@link {{EntityName}}} 聚合根的仓储接口,作为领域层的端口,
 * 由基础设施层的仓储实现提供具体的持久化逻辑。
 *
 * <p>遵循端口-适配器模式,领域层通过此接口与持久化机制解耦,
 * 基础设施层负责实现数据库访问、ORM映射和事务管理。
 */
public interface {{EntityName}}Repository {

  /**
   * 持久化提供的聚合根。
   *
   * <p>保存新的或更新现有的记录。实现需要处理:
   *
   * <ul>
   *   <li>为新记录生成主键ID并通过 {@link {{EntityName}}#assignId} 回填
   *   <li>通过 {@link {{EntityName}}#updateVersion} 更新乐观锁版本号
   *   <li>确保唯一约束不被违反
   * </ul>
   *
   * @param entity 要存储的聚合根
   * @return 持久化后的聚合根(已填充标识符和版本号)
   */
  {{EntityName}} save({{EntityName}} entity);

  {{#if has_business_key}}
  /**
   * 通过{{business_key_description}}加载聚合根。
   *
   * @param {{business_key_param}} {{business_key_param_comment}}
   * @return 存在时返回聚合根,否则返回空
   */
  Optional<{{EntityName}}> findBy{{business_key_name}}({{business_key_type}} {{business_key_param}});
  {{/if}}

  /**
   * 通过主键ID加载聚合根。
   *
   * @param id 主键ID
   * @return 存在时返回聚合根,否则返回空
   */
  Optional<{{EntityName}}> findById(Long id);
}
```

---

## 🛠️ Infra 层：数据对象（DO，继承 BaseDO）

```java
package com.patra.{{module}}.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * {{entity_description}}数据对象。
 *
 * <p>表示 <code>{{table_name}}</code> 表的数据对象(DO),用于MyBatis-Plus的ORM映射。
 * 继承自 {@link BaseDO},包含标准的审计字段(id、version、创建人、更新人、软删除标志等)。
 *
 * <p>数据对象是持久化层的一部分,负责数据库表结构与Java对象之间的映射,
 * 通过转换器({@code {{EntityName}}Converter})与领域聚合根进行转换。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "{{table_name}}", autoResultMap = true)
public class {{EntityName}}DO extends BaseDO {

  {{#each business_fields_do}}
  /** {{field_comment}} */
  @TableField("{{column_name}}")
  private {{java_type}} {{field_name}};

  {{/each}}

  {{#each json_fields}}
  /** {{field_comment}} */
  @TableField(value = "{{column_name}}", typeHandler = JacksonTypeHandler.class)
  private JsonNode {{field_name}};

  {{/each}}

  {{#each enum_fields}}
  /** {{field_comment}} */
  @TableField("{{column_name}}")
  private String {{field_name}};

  {{/each}}

  {{#each timestamp_fields}}
  /** {{field_comment}} */
  @TableField("{{column_name}}")
  private Instant {{field_name}};

  {{/each}}
}
```

**注意**：`BaseDO` 已包含以下字段，无需重复定义：
```java
- id: BIGINT UNSIGNED (主键)
- recordRemarks: JSON (备注)
- version: BIGINT UNSIGNED (乐观锁)
- ipAddress: VARBINARY(16) (IP地址)
- createdAt: TIMESTAMP(6) (创建时间)
- createdBy: BIGINT UNSIGNED (创建人ID)
- createdByName: VARCHAR(100) (创建人姓名)
- updatedAt: TIMESTAMP(6) (更新时间)
- updatedBy: BIGINT UNSIGNED (更新人ID)
- updatedByName: VARCHAR(100) (更新人姓名)
- deleted: TINYINT(1) (软删除)
```

---

## 🔧 Infra 层：Mapper 接口

```java
package com.patra.{{module}}.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.{{module}}.infra.persistence.entity.{{EntityName}}DO;
import org.apache.ibatis.annotations.Mapper;

/**
 * {{entity_description}} MyBatis Mapper。
 */
@Mapper
public interface {{EntityName}}Mapper extends BaseMapper<{{EntityName}}DO> {

  // 继承 BaseMapper 的 CRUD 方法
  // 如需自定义 SQL，在此添加方法并在 XML 中实现
}
```

---

## 🔄 Infra 层：转换器（聚合根 ↔ DO）

```java
package com.patra.{{module}}.infra.persistence.converter;

import com.patra.{{module}}.domain.model.aggregate.{{EntityName}};
import com.patra.{{module}}.domain.model.enums.*;
import com.patra.{{module}}.domain.model.vo.*;
import com.patra.{{module}}.infra.persistence.entity.{{EntityName}}DO;
import org.springframework.stereotype.Component;

/**
 * {{EntityName}} 聚合根与数据对象之间的转换器。
 */
@Component
public class {{EntityName}}Converter {

  /**
   * 聚合根 → 数据对象。
   *
   * @param entity 聚合根
   * @return 数据对象
   */
  public {{EntityName}}DO toDO({{EntityName}} entity) {
    if (entity == null) {
      return null;
    }

    {{EntityName}}DO dobj = new {{EntityName}}DO();

    // 基础字段
    dobj.setId(entity.getId());

    // 业务字段转换
    {{#each field_mappings}}
    dobj.set{{field_name_pascal}}({{mapping_expression}});
    {{/each}}

    // 审计字段
    dobj.setRecordRemarks(entity.getRecordRemarks());
    dobj.setVersion(entity.getVersion());
    dobj.setIpAddress(entity.getIpAddress());
    dobj.setCreatedAt(entity.getCreatedAt());
    dobj.setCreatedBy(entity.getCreatedBy());
    dobj.setCreatedByName(entity.getCreatedByName());
    dobj.setUpdatedAt(entity.getUpdatedAt());
    dobj.setUpdatedBy(entity.getUpdatedBy());
    dobj.setUpdatedByName(entity.getUpdatedByName());
    dobj.setDeleted(entity.getDeleted());

    return dobj;
  }

  /**
   * 数据对象 → 聚合根。
   *
   * @param dobj 数据对象
   * @return 聚合根
   */
  public {{EntityName}} toAggregate({{EntityName}}DO dobj) {
    if (dobj == null) {
      return null;
    }

    return {{EntityName}}.restore(
        dobj.getId(),
        {{#each restore_args}}
        {{arg_expression}}{{#unless @last}},{{/unless}}
        {{/each}},
        // 审计字段
        dobj.getRecordRemarks(),
        dobj.getVersion(),
        dobj.getIpAddress(),
        dobj.getCreatedAt(),
        dobj.getCreatedBy(),
        dobj.getCreatedByName(),
        dobj.getUpdatedAt(),
        dobj.getUpdatedBy(),
        dobj.getUpdatedByName(),
        dobj.getDeleted()
    );
  }
}
```

---

## 🔌 Infra 层：仓储实现

```java
package com.patra.{{module}}.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.patra.{{module}}.domain.model.aggregate.{{EntityName}};
import com.patra.{{module}}.domain.port.{{EntityName}}Repository;
import com.patra.{{module}}.infra.persistence.converter.{{EntityName}}Converter;
import com.patra.{{module}}.infra.persistence.entity.{{EntityName}}DO;
import com.patra.{{module}}.infra.persistence.mapper.{{EntityName}}Mapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * {{EntityName}} 仓储实现。
 *
 * <p>适配器模式：将 MyBatis-Plus 适配到领域层的仓储接口。
 */
@Repository
@RequiredArgsConstructor
public class {{EntityName}}RepositoryMpImpl implements {{EntityName}}Repository {

  private final {{EntityName}}Mapper mapper;
  private final {{EntityName}}Converter converter;

  @Override
  public {{EntityName}} save({{EntityName}} entity) {
    {{EntityName}}DO dobj = converter.toDO(entity);

    if (dobj.getId() == null) {
      // 新增
      mapper.insert(dobj);
      entity.assignId(dobj.getId());
    } else {
      // 更新（乐观锁）
      int rows = mapper.updateById(dobj);
      if (rows == 0) {
        throw new OptimisticLockException("乐观锁冲突，更新失败");
      }
    }

    // 更新版本号
    entity.updateVersion(dobj.getVersion());

    return entity;
  }

  {{#if has_business_key}}
  @Override
  public Optional<{{EntityName}}> findBy{{business_key_name}}({{business_key_type}} {{business_key_param}}) {
    LambdaQueryWrapper<{{EntityName}}DO> wrapper = new LambdaQueryWrapper<>();
    {{#each business_key_conditions}}
    wrapper.eq({{EntityName}}DO::get{{field_name}}, {{param_expression}});
    {{/each}}

    {{EntityName}}DO dobj = mapper.selectOne(wrapper);
    return Optional.ofNullable(converter.toAggregate(dobj));
  }
  {{/if}}

  @Override
  public Optional<{{EntityName}}> findById(Long id) {
    {{EntityName}}DO dobj = mapper.selectById(id);
    return Optional.ofNullable(converter.toAggregate(dobj));
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
| 数据对象 | 大驼峰 + DO 后缀 | `FileMetadataDO` |
| 仓储接口 | 大驼峰 + Repository 后缀 | `FileMetadataRepository` |
| 仓储实现 | 大驼峰 + RepositoryMpImpl 后缀 | `FileMetadataRepositoryMpImpl` |

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
      ├── entity/        # 数据对象（DO）
      ├── mapper/        # MyBatis Mapper
      ├── converter/     # 转换器
      └── repository/    # 仓储实现
```

### 3. 审计字段使用 BaseDO

所有 DO 都继承 `BaseDO`，包含标准审计字段，无需重复定义。

### 4. 时间类型统一使用 Instant

- Domain 层：`java.time.Instant`
- Infra 层（DO）：`java.time.Instant`
- 数据库：`TIMESTAMP(6)`

### 5. 枚举存储为字符串代码

- Domain 层：枚举类型（如 `FileStatus`）
- Infra 层（DO）：`String` 类型
- 数据库：`VARCHAR(32)`
- 转换器负责枚举与字符串的转换

---

## 📖 相关资源

- [六边形架构](https://alistair.cockburn.us/hexagonal-architecture/)
- [MyBatis-Plus 官方文档](https://baomidou.com/)
- [Patra 项目开发规范](../../../../CLAUDE.md)