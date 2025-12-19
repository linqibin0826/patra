package com.patra.registry.infra.adapter.persistence.entity.dictionary;

import com.patra.starter.jpa.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/// 系统字典项别名 JPA 实体，映射到表 `sys_dict_item_alias`。
///
/// 提供外部别名，使合作伙伴系统可以引用内部字典项。
///
/// 数据库规则：
///
/// - `(source_system, external_code)` 全局唯一。
/// - 别名可软删除，可能与禁用的字典项共存。
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(name = "sys_dict_item_alias")
public class SysDictItemAliasEntity extends BaseJpaEntity {

  /// 此别名解析到的字典项的标识符(`sys_dict_item.id`)。
  @Column(name = "item_id", nullable = false)
  private Long itemId;

  /// 外部系统标识符(例如，`pubmed`，`crossref`)。
  @Column(name = "source_system", nullable = false, length = 50)
  private String sourceSystem;

  /// 上游系统为引用项提供的外部代码。
  @Column(name = "external_code", nullable = false, length = 100)
  private String externalCode;

  /// 可选的来自上游系统的可读标签。
  @Column(name = "external_label", length = 200)
  private String externalLabel;

  /// 可选的备注，描述映射上下文或任何特殊行为。
  @Column(name = "notes", length = 500)
  private String notes;
}
