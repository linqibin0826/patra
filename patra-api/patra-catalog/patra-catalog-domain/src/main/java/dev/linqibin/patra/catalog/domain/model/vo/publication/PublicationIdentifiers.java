package dev.linqibin.patra.catalog.domain.model.vo.publication;

import cn.hutool.core.lang.Assert;
import java.io.Serial;
import java.io.Serializable;

/// 文献标识符值对象。封装文献的多种标识符（PMID、DOI 等）。
///
/// 设计原则：
///
/// - 不可变性：Record 自动提供
///   - 至少包含一个非空标识符
///   - PMID 和 DOI 是最常用标识符，冗余到主表优化查询
///   - 格式验证确保数据质量
///
/// 使用示例：
///
/// ```java
/// // 创建仅包含 PMID 的标识符
/// PublicationIdentifiers ids1 = PublicationIdentifiers.ofPmid("12345678");
///
/// // 创建包含 PMID 和 DOI 的标识符
/// PublicationIdentifiers ids2 = PublicationIdentifiers.of("12345678", "10.1234/example");
///
/// // 判断是否包含特定标识符
/// if (ids2.hasDoi()) {
///     System.out.println("DOI: " + ids2.doi());
/// ```
///
/// @param pmid PubMed ID（冗余优化，1-15位数字）
/// @param doi Digital Object Identifier（冗余优化，最长200字符）
/// @author linqibin
/// @since 0.1.0
public record PublicationIdentifiers(String pmid, String doi) implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：验证标识符的有效性。
  ///
  /// @throws IllegalArgumentException 如果所有标识符都为空，或格式无效
  public PublicationIdentifiers {
    // 至少一个标识符非空
    Assert.isTrue(pmid != null || doi != null, "至少需要提供一个标识符（PMID 或 DOI）");

    // PMID 格式验证（1-15位数字）
    if (pmid != null) {
      Assert.isTrue(pmid.matches("\\d{1,15}"), "PMID 格式无效，必须为1-15位数字：%s", pmid);
    }

    // DOI 格式验证（基本格式：10.xxxx/yyyy，最长200字符）
    if (doi != null) {
      Assert.isTrue(
          doi.startsWith("10.") && doi.length() <= 200, "DOI 格式无效，必须以 '10.' 开头且长度不超过200：%s", doi);
    }
  }

  /// 创建仅包含 PMID 的标识符。
  ///
  /// @param pmid PubMed ID
  /// @return 标识符值对象
  /// @throws IllegalArgumentException 如果 PMID 格式无效
  public static PublicationIdentifiers ofPmid(String pmid) {
    return new PublicationIdentifiers(pmid, null);
  }

  /// 创建仅包含 DOI 的标识符。
  ///
  /// @param doi Digital Object Identifier
  /// @return 标识符值对象
  /// @throws IllegalArgumentException 如果 DOI 格式无效
  public static PublicationIdentifiers ofDoi(String doi) {
    return new PublicationIdentifiers(null, doi);
  }

  /// 创建包含 PMID 和 DOI 的标识符。
  ///
  /// @param pmid PubMed ID
  /// @param doi Digital Object Identifier
  /// @return 标识符值对象
  /// @throws IllegalArgumentException 如果两个标识符都为空，或格式无效
  public static PublicationIdentifiers of(String pmid, String doi) {
    return new PublicationIdentifiers(pmid, doi);
  }

  /// 判断是否包含 PMID。
  ///
  /// @return true 如果包含 PMID
  public boolean hasPmid() {
    return pmid != null;
  }

  /// 判断是否包含 DOI。
  ///
  /// @return true 如果包含 DOI
  public boolean hasDoi() {
    return doi != null;
  }

  /// 获取主要标识符（优先返回 PMID，其次 DOI）。
  ///
  /// @return 主要标识符
  public String getPrimaryIdentifier() {
    return pmid != null ? pmid : doi;
  }

  /// 获取标识符的显示文本。
  ///
  /// @return 格式化的标识符文本
  public String toDisplayString() {
    if (pmid != null && doi != null) {
      return String.format("PMID:%s, DOI:%s", pmid, doi);
    } else if (pmid != null) {
      return "PMID:" + pmid;
    } else {
      return "DOI:" + doi;
    }
  }
}
