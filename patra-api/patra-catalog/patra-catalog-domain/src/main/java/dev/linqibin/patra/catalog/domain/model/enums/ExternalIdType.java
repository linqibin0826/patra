package dev.linqibin.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/// 外部标识符类型枚举。
///
/// 字段映射：cat_organization_external_id.id_type
///
/// 基于 ROR (Research Organization Registry) Schema v2.0 定义的外部标识符类型。
/// 扩展 RINGGOLD 以支持更多机构标识符场景。
///
/// **标识符说明**：
///
/// | 类型 | 说明 | 示例值 |
/// |------|------|--------|
/// | GRID | Global Research Identifier Database（已合并到 ROR） | grid.38142.3c |
/// | ISNI | International Standard Name Identifier | 0000 0001 2157 6568 |
/// | WIKIDATA | Wikidata Q identifier | Q219563 |
/// | FUNDREF | Crossref Funder Registry ID | 100000001 |
/// | RINGGOLD | Ringgold Identifier | 1812 |
///
/// **标识符特点**：
///
/// - **GRID**：已于 2021 年合并到 ROR，但历史数据仍保留
/// - **ISNI**：国际标准名称标识符，ISO 27729
/// - **WIKIDATA**：维基数据实体 ID，可获取丰富的元数据
/// - **FUNDREF**：Crossref 资助机构注册 ID
/// - **RINGGOLD**：商业机构标识符，常用于出版商系统
///
/// @author linqibin
/// @since 0.1.0
/// @see <a href="https://ror.readme.io/docs/fields#external_ids">ROR External IDs</a>
@Getter
public enum ExternalIdType {

  /// GRID ID（已合并到 ROR，仅保留历史数据）
  GRID("grid", "GRID ID"),

  /// ISNI（国际标准名称标识符）
  ISNI("isni", "ISNI"),

  /// Wikidata QID
  WIKIDATA("wikidata", "Wikidata QID"),

  /// FundRef ID（Crossref 资助机构注册 ID）
  FUNDREF("fundref", "FundRef ID"),

  /// Ringgold ID
  RINGGOLD("ringgold", "Ringgold ID");

  /// 数据库存储的代码值（与 ROR 一致，小写）
  private final String code;

  /// 中文描述
  private final String description;

  ExternalIdType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /// 从代码值解析枚举（不区分大小写）。
  ///
  /// @param value 代码值（如 "grid", "ISNI"）
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果代码值为空或无效
  public static ExternalIdType fromCode(String value) {
    Assert.notBlank(value, "外部标识符类型代码不能为空");
    String normalized = value.trim().toLowerCase();
    for (ExternalIdType type : values()) {
      if (type.code.equals(normalized)) {
        return type;
      }
    }
    throw new IllegalArgumentException("未知的外部标识符类型：" + value);
  }

  /// 尝试从代码值解析枚举，如果无法识别则返回 null。
  ///
  /// @param value 代码值
  /// @return 对应的枚举值，无法识别则返回 null
  public static ExternalIdType fromCodeOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim().toLowerCase();
    for (ExternalIdType type : values()) {
      if (type.code.equals(normalized)) {
        return type;
      }
    }
    return null;
  }

  /// 判断是否为 ROR 原生支持的标识符类型。
  ///
  /// ROR Schema v2.0 原生支持：grid, isni, wikidata, fundref
  ///
  /// @return true 如果为 ROR 原生支持的类型
  public boolean isRorNative() {
    return this == GRID || this == ISNI || this == WIKIDATA || this == FUNDREF;
  }

  /// 判断是否为已废弃的标识符类型。
  ///
  /// GRID 已于 2021 年合并到 ROR，不再更新。
  ///
  /// @return true 如果为已废弃的类型
  public boolean isDeprecated() {
    return this == GRID;
  }

  /// 判断是否为资助相关标识符。
  ///
  /// @return true 如果为 FUNDREF
  public boolean isFunderRelated() {
    return this == FUNDREF;
  }
}
