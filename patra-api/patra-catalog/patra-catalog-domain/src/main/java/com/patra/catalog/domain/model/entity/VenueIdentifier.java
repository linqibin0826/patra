package com.patra.catalog.domain.model.entity;

import cn.hutool.core.lang.Assert;
import com.patra.catalog.domain.model.enums.VenueIdentifierType;
import java.io.Serial;
import java.io.Serializable;

/// 载体标识符值对象（不可变）。
///
/// **设计说明**：
///
/// - 作为 VenueAggregate 的值对象存在（不是实体）
/// - 使用 Record 实现不可变性
/// - 与 Venue 具有相同的生命周期
/// - 支持多种标识符类型（ISSN/ISBN/OpenAlex/NLM/MAG 等）
///
/// **业务规则**：
///
/// - 标识符类型和值不能为空
/// - ISSN 类型会进行格式验证（`XXXX-XXXX`）
/// - 相等性基于类型 + 值
///
/// **示例**：
///
/// ```java
/// // 创建 OpenAlex ID 标识符
/// VenueIdentifier openalexId = VenueIdentifier.forOpenAlex("S1234567890");
///
/// // 创建 ISSN 标识符
/// VenueIdentifier issn = VenueIdentifier.forIssn("1234-5678");
/// ```
///
/// @param type 标识符类型（必填）
/// @param value 标识符值（必填，ISSN 类型会自动标准化为大写）
/// @author linqibin
/// @since 0.1.0
public record VenueIdentifier(VenueIdentifierType type, String value) implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// ISSN 格式正则表达式
  private static final String ISSN_PATTERN = "\\d{4}-\\d{3}[\\dXx]";

  /// 紧凑构造器：验证参数并标准化 ISSN。
  public VenueIdentifier {
    Assert.notNull(type, "标识符类型不能为空");
    Assert.notBlank(value, "标识符值不能为空");

    // ISSN 格式验证和标准化
    if (type.isIssn()) {
      Assert.isTrue(value.matches(ISSN_PATTERN), "ISSN 格式无效，必须符合 'XXXX-XXXX' 格式：{}", value);
      // 标准化为大写（X 可能是小写）
      value = value.toUpperCase();
    }
  }

  // ========== 工厂方法 ==========

  /// 创建 OpenAlex ID 标识符。
  ///
  /// @param openalexId OpenAlex Source ID
  /// @return 标识符值对象
  public static VenueIdentifier forOpenAlex(String openalexId) {
    return new VenueIdentifier(VenueIdentifierType.OPENALEX, openalexId);
  }

  /// 创建 ISSN 标识符。
  ///
  /// @param issn ISSN 值
  /// @return 标识符值对象
  public static VenueIdentifier forIssn(String issn) {
    return new VenueIdentifier(VenueIdentifierType.ISSN, issn);
  }

  /// 创建 Linking ISSN 标识符。
  ///
  /// @param issnL Linking ISSN 值
  /// @return 标识符值对象
  public static VenueIdentifier forIssnL(String issnL) {
    return new VenueIdentifier(VenueIdentifierType.ISSN_L, issnL);
  }

  /// 创建 NLM ID 标识符。
  ///
  /// @param nlmId NLM 唯一标识符
  /// @return 标识符值对象
  public static VenueIdentifier forNlm(String nlmId) {
    return new VenueIdentifier(VenueIdentifierType.NLM, nlmId);
  }

  /// 创建 CODEN 标识符。
  ///
  /// CODEN 是一种 6 字符的期刊标识符，来源于 NLM Serfile。
  ///
  /// @param coden CODEN 编码（6字符）
  /// @return 标识符值对象
  public static VenueIdentifier forCoden(String coden) {
    return new VenueIdentifier(VenueIdentifierType.CODEN, coden);
  }

  // ========== 便捷判断方法 ==========

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
    return String.format("VenueIdentifier[type=%s, value=%s]", type.getCode(), value);
  }
}
