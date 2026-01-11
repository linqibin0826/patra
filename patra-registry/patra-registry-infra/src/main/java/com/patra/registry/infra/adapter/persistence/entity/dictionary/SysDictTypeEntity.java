package com.patra.registry.infra.adapter.persistence.entity.dictionary;

import com.patra.starter.jpa.entity.SoftDeletableJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

/// 系统字典类型 JPA 实体，映射到表 `sys_dict_type`。
///
/// 向 CQRS 堆栈的查询侧暴露字典类型元数据。
///
/// 重要的数据库级不变量：
///
/// - `type_code` 在所有类型中唯一，作为业务键。
/// - 系统类型 (`is_system = 1`) 由平台管理，通常不可变。
/// - 标记为 `allow_custom_items = 1` 的类型可由业务用户扩展。
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(name = "sys_dict_type")
public class SysDictTypeEntity extends SoftDeletableJpaEntity {

  /// 字典类型的稳定业务键(例如，`http_method`)。预期格式为小写蛇形命名。
  @Column(name = "type_code", nullable = false, length = 64)
  private String typeCode;

  /// 供 UI 组件使用的可读显示名称。
  @Column(name = "type_name", length = 200)
  private String typeName;

  /// 可选的自由格式描述，记录使用指南。
  @Column(name = "description", length = 500)
  private String description;

  /// 指示是否允许业务用户创建自定义项目。
  @Column(name = "allow_custom_items")
  private Boolean allowCustomItems;

  /// 标记此字典类型是否由平台管理(`true`)或业务管理(`false`)的标志。
  @Column(name = "is_system")
  private Boolean isSystem;

  /// 可选的 JSON 载荷，用于附加元数据(例如，颜色、图标原型)。
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "reserved_json", columnDefinition = "JSON")
  private JsonNode reservedJson;
}
