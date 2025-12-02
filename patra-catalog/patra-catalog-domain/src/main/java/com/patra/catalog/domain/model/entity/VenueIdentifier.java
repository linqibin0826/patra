package com.patra.catalog.domain.model.entity;

import cn.hutool.core.lang.Assert;
import com.patra.catalog.domain.model.enums.VenueIdentifierType;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import lombok.Getter;

/// 载体标识符实体（聚合内实体，不是聚合根）。
///
/// 设计说明：
///
/// - 作为 VenueAggregate 的聚合内实体存在
/// - 与 Venue 具有相同的生命周期
/// - 支持多种标识符类型（ISSN/ISBN/OpenAlex/NLM/MAG 等）
///
/// 业务规则：
///
/// - 同一类型标识符可以有多个（如期刊可有 Print ISSN 和 Electronic ISSN）
/// - 每种类型可以设置一个首选标识符（isPrimary=true）
/// - 标识符值不能为空
///
/// 使用示例：
///
/// ```java
/// // 创建 OpenAlex ID 标识符
/// VenueIdentifier openalexId = VenueIdentifier.create(
///     VenueIdentifierType.OPENALEX,
///     "S1234567890",
///     true
/// );
///
/// // 创建 ISSN 标识符
/// VenueIdentifier issn = VenueIdentifier.create(
///     VenueIdentifierType.ISSN,
///     "1234-5678",
///     false
/// );
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Getter
public class VenueIdentifier implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// ISSN 格式正则表达式
  private static final String ISSN_PATTERN = "\\d{4}-\\d{3}[\\dXx]";

  // ========== 标识符 ==========

  /// 主键 ID（由 Repository 在持久化时分配）
  private Long id;

  // ========== 业务字段 ==========

  /// 标识符类型
  private final VenueIdentifierType type;

  /// 标识符值
  private final String value;

  /// 是否首选标识符（同类型中的首选）
  private boolean isPrimary;

  /// 私有构造函数。
  ///
  /// @param id 主键 ID（新建时为 null）
  /// @param type 标识符类型
  /// @param value 标识符值
  /// @param isPrimary 是否首选
  private VenueIdentifier(Long id, VenueIdentifierType type, String value, boolean isPrimary) {
    Assert.notNull(type, "标识符类型不能为空");
    Assert.notBlank(value, "标识符值不能为空");

    // ISSN 格式验证
    if (type.isIssn()) {
      Assert.isTrue(value.matches(ISSN_PATTERN), "ISSN 格式无效，必须符合 'XXXX-XXXX' 格式：%s", value);
      // 标准化为大写（X 可能是小写）
      value = value.toUpperCase();
    }

    this.id = id;
    this.type = type;
    this.value = value;
    this.isPrimary = isPrimary;
  }

  // ========== 工厂方法 ==========

  /// 创建标识符。
  ///
  /// @param type 标识符类型
  /// @param value 标识符值
  /// @param isPrimary 是否首选
  /// @return 标识符实体
  public static VenueIdentifier create(VenueIdentifierType type, String value, boolean isPrimary) {
    return new VenueIdentifier(null, type, value, isPrimary);
  }

  /// 创建标识符（非首选）。
  ///
  /// @param type 标识符类型
  /// @param value 标识符值
  /// @return 标识符实体
  public static VenueIdentifier create(VenueIdentifierType type, String value) {
    return new VenueIdentifier(null, type, value, false);
  }

  /// 创建 OpenAlex ID 标识符。
  ///
  /// @param openalexId OpenAlex Source ID
  /// @return 标识符实体
  public static VenueIdentifier forOpenAlex(String openalexId) {
    return create(VenueIdentifierType.OPENALEX, openalexId, true);
  }

  /// 创建 ISSN 标识符。
  ///
  /// @param issn ISSN 值
  /// @param isPrimary 是否首选
  /// @return 标识符实体
  public static VenueIdentifier forIssn(String issn, boolean isPrimary) {
    return create(VenueIdentifierType.ISSN, issn, isPrimary);
  }

  /// 创建 Linking ISSN 标识符。
  ///
  /// @param issnL Linking ISSN 值
  /// @return 标识符实体
  public static VenueIdentifier forIssnL(String issnL) {
    return create(VenueIdentifierType.ISSN_L, issnL, true);
  }

  /// 创建 NLM ID 标识符。
  ///
  /// @param nlmId NLM 唯一标识符
  /// @return 标识符实体
  public static VenueIdentifier forNlm(String nlmId) {
    return create(VenueIdentifierType.NLM, nlmId, true);
  }

  /// 从持久化状态重建实体（由 Repository 使用）。
  ///
  /// @param id 主键 ID
  /// @param type 标识符类型
  /// @param value 标识符值
  /// @param isPrimary 是否首选
  /// @return 重建的实体
  public static VenueIdentifier restore(
      Long id, VenueIdentifierType type, String value, boolean isPrimary) {
    return new VenueIdentifier(id, type, value, isPrimary);
  }

  // ========== 业务方法 ==========

  /// 设置 ID（由 Repository 在持久化后回写）。
  ///
  /// @param id 主键 ID
  public void assignId(Long id) {
    this.id = id;
  }

  /// 设置为首选标识符。
  public void markAsPrimary() {
    this.isPrimary = true;
  }

  /// 取消首选标识符。
  public void unmarkAsPrimary() {
    this.isPrimary = false;
  }

  /// 判断是否为 OpenAlex ID。
  ///
  /// @return true 如果为 OpenAlex ID
  public boolean isOpenAlexId() {
    return type.isOpenAlex();
  }

  /// 判断是否为 ISSN（包括 Linking ISSN）。
  ///
  /// @return true 如果为 ISSN 类型
  public boolean isIssnId() {
    return type.isIssn();
  }

  /// 判断是否为标准出版标识符（ISSN/ISBN）。
  ///
  /// @return true 如果为标准出版标识符
  public boolean isStandardPublishingId() {
    return type.isStandardPublishingId();
  }

  @Override
  public String toString() {
    return String.format(
        "VenueIdentifier[type=%s, value=%s, primary=%b]", type.getCode(), value, isPrimary);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof VenueIdentifier that)) {
      return false;
    }
    // 业务相等性：类型 + 值
    return type == that.type && Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, value);
  }
}
