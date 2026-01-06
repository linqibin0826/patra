package com.patra.catalog.domain.model.vo.organization;

import cn.hutool.core.lang.Assert;
import com.patra.catalog.domain.model.enums.ExternalIdType;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/// 外部标识符值对象。
///
/// 字段映射：cat_organization_external_id 表
///
/// 基于 ROR Schema v2.0 的 external_ids 字段定义。支持同类型多个值
/// （如一个机构可能有多个 ISNI），并标记首选值。
///
/// **字段说明**：
///
/// | 字段 | 说明 |
/// |------|------|
/// | id | 数据库主键（雪花 ID，持久化后填充） |
/// | type | 标识符类型（GRID/ISNI/WIKIDATA/FUNDREF/RINGGOLD） |
/// | allValues | 所有值列表 |
/// | preferred | 首选值（用于显示和匹配） |
///
/// **ROR Schema 结构**：
///
/// ```json
/// "external_ids": {
///   "isni": {
///     "all": ["0000 0001 2157 6568"],
///     "preferred": "0000 0001 2157 6568"
///   },
///   "fundref": {
///     "all": ["100000001", "100000002"],
///     "preferred": "100000001"
///   }
/// }
/// ```
///
/// **相等性**：基于 type（每个机构每种类型只有一条记录）
///
/// @param id 数据库主键（持久化后填充）
/// @param type 标识符类型
/// @param allValues 所有值列表
/// @param preferred 首选值
/// @author linqibin
/// @since 0.1.0
/// @see <a href="https://ror.readme.io/docs/fields#external_ids">ROR External IDs</a>
@SuppressWarnings("java:S6218") // 自定义 equals/hashCode 基于业务语义（type）
public record ExternalId(Long id, ExternalIdType type, List<String> allValues, String preferred)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：验证参数。
  public ExternalId {
    Assert.notNull(type, "外部标识符类型不能为空");
    Assert.notEmpty(allValues, "外部标识符值不能为空");
    Assert.notBlank(preferred, "首选值不能为空");
    // 防御性拷贝，确保不可变
    allValues = List.copyOf(allValues);
  }

  // ========== 工厂方法 ==========

  /// 创建单值外部标识符。
  ///
  /// @param type 标识符类型
  /// @param value 标识符值
  /// @return 外部标识符值对象
  public static ExternalId create(ExternalIdType type, String value) {
    return new ExternalId(null, type, List.of(value), value);
  }

  /// 创建多值外部标识符。
  ///
  /// @param type 标识符类型
  /// @param allValues 所有值列表
  /// @param preferred 首选值
  /// @return 外部标识符值对象
  public static ExternalId create(ExternalIdType type, List<String> allValues, String preferred) {
    return new ExternalId(null, type, allValues, preferred);
  }

  /// 创建带 ID 的外部标识符（用于从数据库加载）。
  ///
  /// @param id 数据库主键
  /// @param type 标识符类型
  /// @param allValues 所有值列表
  /// @param preferred 首选值
  /// @return 外部标识符值对象
  public static ExternalId createWithId(
      Long id, ExternalIdType type, List<String> allValues, String preferred) {
    return new ExternalId(id, type, allValues, preferred);
  }

  // ========== with-style 方法 ==========

  /// 添加 ID（返回新实例）。
  ///
  /// @param id 数据库主键
  /// @return 带 ID 的新实例
  public ExternalId withId(Long id) {
    return new ExternalId(id, this.type, this.allValues, this.preferred);
  }

  // ========== 查询方法 ==========

  /// 判断是否已持久化（有 ID）。
  ///
  /// @return true 如果已持久化
  public boolean hasId() {
    return id != null;
  }

  /// 判断是否有多个值。
  ///
  /// @return true 如果有多个值
  public boolean hasMultipleValues() {
    return allValues.size() > 1;
  }

  /// 判断是否为 GRID 标识符。
  ///
  /// @return true 如果是 GRID
  public boolean isGrid() {
    return type == ExternalIdType.GRID;
  }

  /// 判断是否为 ISNI 标识符。
  ///
  /// @return true 如果是 ISNI
  public boolean isIsni() {
    return type == ExternalIdType.ISNI;
  }

  /// 判断是否为 Wikidata 标识符。
  ///
  /// @return true 如果是 WIKIDATA
  public boolean isWikidata() {
    return type == ExternalIdType.WIKIDATA;
  }

  /// 判断是否为 FundRef 标识符。
  ///
  /// @return true 如果是 FUNDREF
  public boolean isFundRef() {
    return type == ExternalIdType.FUNDREF;
  }

  /// 判断是否为 Ringgold 标识符。
  ///
  /// @return true 如果是 RINGGOLD
  public boolean isRinggold() {
    return type == ExternalIdType.RINGGOLD;
  }

  @Override
  public String toString() {
    return String.format("ExternalId[type=%s, preferred=%s]", type, preferred);
  }

  /// 业务相等性：type（每个机构每种类型只有一条记录）。
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ExternalId that)) {
      return false;
    }
    return type == that.type;
  }

  @Override
  public int hashCode() {
    return Objects.hash(type);
  }
}
