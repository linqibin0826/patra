package com.patra.registry.infra.adapter.persistence.entity.dictionary;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.jpa.entity.SoftDeletableJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/// 系统字典项 JPA 实体，映射到表 `sys_dict_item`。
///
/// 表示属于字典类型的单个字典项。
///
/// 数据库级要点：
///
/// - 每个类型最多可以有一个默认项(通过 `default_key` 强制)。
/// - `item_code` 在 `type_id` 边界内必须唯一。
/// - `item_code` 预期使用大写蛇形命名(例如，`GET`，`PAGE_NUMBER`)。
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(name = "sys_dict_item")
public class SysDictItemEntity extends SoftDeletableJpaEntity {

  /// 所属字典类型的标识符(`sys_dict_type.id`)。
  @Column(name = "type_id", nullable = false)
  private Long typeId;

  /// 在所属类型内稳定的项目代码。
  @Column(name = "item_code", nullable = false, length = 64)
  private String itemCode;

  /// 向最终用户展示的可读项目名称。
  @Column(name = "item_name", length = 200)
  private String itemName;

  /// 可选的短名称或缩写，用于紧凑的 UI 布局。
  @Column(name = "short_name", length = 64)
  private String shortName;

  /// 可选的描述，记录语义和使用提示。
  @Column(name = "description", length = 500)
  private String description;

  /// 显示顺序(升序)。较小的数字排在前面。
  @Column(name = "display_order")
  private Integer displayOrder;

  /// 指示该项目是否为其类型的默认选择。
  @Column(name = "is_default")
  private Boolean isDefault;

  /// 标记项目是否启用并应对业务层可用的标志。
  @Column(name = "enabled")
  private Boolean enabled;

  /// 可选的标签颜色(十六进制如 `#AABBCC` 或语义名称)。
  @Column(name = "label_color", length = 32)
  private String labelColor;

  /// 可选的图标引用，供 UI 表面使用。
  @Column(name = "icon_name", length = 64)
  private String iconName;

  /// 可扩展的 JSON 载荷，用于附加业务属性。
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "attributes_json", columnDefinition = "JSON")
  private JsonNode attributesJson;

  /// 生成列，用于强制每个类型只有一个默认项(由数据库处理)。
  @Column(name = "default_key")
  private Long defaultKey;
}
