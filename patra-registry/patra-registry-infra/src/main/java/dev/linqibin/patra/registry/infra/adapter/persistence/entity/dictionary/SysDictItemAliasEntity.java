package dev.linqibin.patra.registry.infra.adapter.persistence.entity.dictionary;

import dev.linqibin.starter.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/// 系统字典项别名 JPA 实体，映射到表 `sys_dict_item_alias`。
///
/// 提供外部别名，使上游标准或外部系统可以引用内部字典项。
///
/// 数据库规则：
///
/// - `(source_standard, external_code)` 全局唯一。
/// - 别名使用物理删除，不支持软删除。
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(name = "sys_dict_item_alias")
public class SysDictItemAliasEntity extends ValueObjectJpaEntity {

  /// 此别名解析到的字典项的标识符(`sys_dict_item.id`)。
  @Column(name = "item_id", nullable = false)
  private Long itemId;

  /// 来源标准标识符(例如，`iso_3166_1_alpha2`，`global`)。
  @Column(name = "source_standard", nullable = false, length = 64)
  private String sourceStandard;

  /// 上游系统为引用项提供的外部代码。
  @Column(name = "external_code", nullable = false, length = 128)
  private String externalCode;

  /// 可选的来自上游系统的可读标签。
  @Column(name = "external_label", length = 200)
  private String externalLabel;

  /// 可选的备注，描述映射上下文或任何特殊行为。
  @Column(name = "notes", length = 500)
  private String notes;
}
