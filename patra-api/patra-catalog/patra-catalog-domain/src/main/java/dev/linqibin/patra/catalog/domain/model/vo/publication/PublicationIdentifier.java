package dev.linqibin.patra.catalog.domain.model.vo.publication;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import dev.linqibin.patra.common.model.enums.PublicationIdentifierType;
import java.io.Serial;
import java.io.Serializable;
import java.util.Locale;

/// 文献标识符值对象（单个标识符）。
///
/// 表示文献的一个外部标识符，支持多种类型（PMID、DOI、PMC、PII、arXiv 等）。
///
/// **与 PublicationIdentifiers 的区别**：
///
/// - `PublicationIdentifiers`：冗余优化字段（pmid + doi），存储在主表，用于高频查询
/// - `PublicationIdentifier`：完整标识符，存储在子表，支持多种类型和来源追踪
///
/// **聚合边界内管理**：
///
/// - 标识符是 Publication 聚合的组成部分
/// - 通过 `PublicationAggregate.addIdentifier()` 添加
/// - 保护标识符唯一性不变量
///
/// **验证规则**：
///
/// - PMID：1-15 位数字
/// - DOI：以 "10." 开头，长度 ≤ 200
/// - PMC：以 "PMC" 开头，后跟数字
/// - 其他类型：仅验证非空
///
/// 使用示例：
///
/// ```java
/// // 创建 PMID 标识符
/// PublicationIdentifier pmid = PublicationIdentifier.forPmid("38123456");
///
/// // 创建带来源的 DOI 标识符
/// PublicationIdentifier doi = PublicationIdentifier.forDoi("10.1038/nature12345", "Crossref");
///
/// // 创建 PMC 标识符
/// PublicationIdentifier pmc = PublicationIdentifier.forPmc("PMC1234567");
///
/// // 通用创建
/// PublicationIdentifier other = PublicationIdentifier.of(PublicationIdentifierType.PII,
// "S0140-6736(21)00123-4", "PubMed");
/// ```
///
/// @param type 标识符类型
/// @param value 标识符值
/// @param source 标识符来源（如 "PubMed"、"Crossref"、"Manual"）
/// @author linqibin
/// @since 0.1.0
public record PublicationIdentifier(PublicationIdentifierType type, String value, String source)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：验证标识符的有效性。
  ///
  /// @throws IllegalArgumentException 如果类型或值为空，或格式不符合类型要求
  public PublicationIdentifier {
    Assert.notNull(type, "标识符类型不能为空");
    Assert.notBlank(value, "标识符值不能为空");

    // 根据类型进行格式验证
    switch (type) {
      case PMID -> Assert.isTrue(value.matches("\\d{1,15}"), "PMID 格式无效，必须为 1-15 位数字：%s", value);
      case DOI ->
          Assert.isTrue(
              value.startsWith("10.") && value.length() <= 200,
              "DOI 格式无效，必须以 '10.' 开头且长度不超过 200：%s",
              value);
      case PMC ->
          Assert.isTrue(
              value.toUpperCase(Locale.ROOT).startsWith("PMC") && value.length() >= 4,
              "PMC ID 格式无效，必须以 'PMC' 开头后跟数字：%s",
              value);
      default -> {
        // 其他类型仅验证非空（已在上面验证）
      }
    }
  }

  /// 创建 PMID 标识符。
  ///
  /// @param pmid PubMed ID（1-15 位数字）
  /// @return PMID 标识符
  /// @throws IllegalArgumentException 如果 PMID 格式无效
  public static PublicationIdentifier forPmid(String pmid) {
    return new PublicationIdentifier(PublicationIdentifierType.PMID, pmid, null);
  }

  /// 创建带来源的 PMID 标识符。
  ///
  /// @param pmid PubMed ID（1-15 位数字）
  /// @param source 来源（如 "PubMed"）
  /// @return PMID 标识符
  public static PublicationIdentifier forPmid(String pmid, String source) {
    return new PublicationIdentifier(PublicationIdentifierType.PMID, pmid, source);
  }

  /// 创建 DOI 标识符。
  ///
  /// @param doi Digital Object Identifier（以 "10." 开头）
  /// @return DOI 标识符
  /// @throws IllegalArgumentException 如果 DOI 格式无效
  public static PublicationIdentifier forDoi(String doi) {
    return new PublicationIdentifier(PublicationIdentifierType.DOI, doi, null);
  }

  /// 创建带来源的 DOI 标识符。
  ///
  /// @param doi Digital Object Identifier（以 "10." 开头）
  /// @param source 来源（如 "Crossref"、"PubMed"）
  /// @return DOI 标识符
  public static PublicationIdentifier forDoi(String doi, String source) {
    return new PublicationIdentifier(PublicationIdentifierType.DOI, doi, source);
  }

  /// 创建 PMC 标识符。
  ///
  /// @param pmcid PubMed Central ID（以 "PMC" 开头）
  /// @return PMC 标识符
  /// @throws IllegalArgumentException 如果 PMC ID 格式无效
  public static PublicationIdentifier forPmc(String pmcid) {
    return new PublicationIdentifier(PublicationIdentifierType.PMC, pmcid, null);
  }

  /// 创建带来源的 PMC 标识符。
  ///
  /// @param pmcid PubMed Central ID（以 "PMC" 开头）
  /// @param source 来源（如 "PMC"）
  /// @return PMC 标识符
  public static PublicationIdentifier forPmc(String pmcid, String source) {
    return new PublicationIdentifier(PublicationIdentifierType.PMC, pmcid, source);
  }

  /// 创建通用标识符。
  ///
  /// @param type 标识符类型
  /// @param value 标识符值
  /// @param source 来源（可为空）
  /// @return 标识符值对象
  public static PublicationIdentifier of(
      PublicationIdentifierType type, String value, String source) {
    return new PublicationIdentifier(type, value, source);
  }

  /// 创建无来源的通用标识符。
  ///
  /// @param type 标识符类型
  /// @param value 标识符值
  /// @return 标识符值对象
  public static PublicationIdentifier of(PublicationIdentifierType type, String value) {
    return new PublicationIdentifier(type, value, null);
  }

  /// 判断是否有来源信息。
  ///
  /// @return true 如果 source 不为空
  public boolean hasSource() {
    return StrUtil.isNotBlank(source);
  }

  /// 判断是否为主要标识符类型（PMID 或 DOI）。
  ///
  /// @return true 如果为 PMID 或 DOI
  public boolean isPrimary() {
    return type.isPrimary();
  }

  /// 判断是否为 PMID。
  ///
  /// @return true 如果为 PMID 类型
  public boolean isPmid() {
    return type == PublicationIdentifierType.PMID;
  }

  /// 判断是否为 DOI。
  ///
  /// @return true 如果为 DOI 类型
  public boolean isDoi() {
    return type == PublicationIdentifierType.DOI;
  }

  /// 判断是否为 PMC ID。
  ///
  /// @return true 如果为 PMC 类型
  public boolean isPmc() {
    return type == PublicationIdentifierType.PMC;
  }

  /// 获取显示文本。
  ///
  /// @return 格式化的标识符文本（如 "PMID:12345678"）
  public String toDisplayString() {
    return type.name() + ":" + value;
  }
}
