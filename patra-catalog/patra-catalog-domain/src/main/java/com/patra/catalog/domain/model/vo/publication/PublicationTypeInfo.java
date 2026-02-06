package com.patra.catalog.domain.model.vo.publication;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import java.io.Serial;
import java.io.Serializable;

/// 出版类型值对象。
///
/// 封装文献的出版类型信息，来自受控词表。
///
/// **命名说明**：
///
/// 使用 `PublicationTypeInfo` 而非 `PublicationType`，避免与枚举类 `PublicationType` 冲突。
///
/// **业务含义**：
///
/// 出版类型描述文献的性质和形式，如：
/// - **Journal Article**：期刊论文
/// - **Review**：综述
/// - **Clinical Trial**：临床试验
/// - **Meta-Analysis**：荟萃分析
/// - **Case Reports**：病例报告
///
/// **词表来源**：
///
/// 不同数据源使用不同的受控词表：
/// - PubMed：使用 MEDLINE Publication Types
/// - OpenAlex：使用 OpenAlex Type taxonomy
/// - Crossref：使用 Crossref Type vocabulary
///
/// @param typeId 类型标识符（来自受控词表）
/// @param typeValue 类型文本描述
/// @param vocabularySource 词表来源
/// @param typeOrder 类型顺序
/// @author linqibin
/// @since 0.1.0
public record PublicationTypeInfo(
    String typeId, String typeValue, String vocabularySource, Integer typeOrder)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：验证类型值不为空。
  public PublicationTypeInfo {
    Assert.isTrue(StrUtil.isNotBlank(typeValue), "出版类型值不能为空");
  }

  /// 创建出版类型值对象。
  ///
  /// @param typeId 类型标识符
  /// @param typeValue 类型文本描述
  /// @param vocabularySource 词表来源
  /// @param order 类型顺序
  /// @return 出版类型值对象
  public static PublicationTypeInfo of(
      String typeId, String typeValue, String vocabularySource, Integer order) {
    return new PublicationTypeInfo(typeId, typeValue, vocabularySource, order);
  }

  /// 判断是否有类型标识符。
  ///
  /// @return true 如果有 typeId
  public boolean hasTypeId() {
    return StrUtil.isNotBlank(typeId);
  }

  /// 判断是否有词表来源。
  ///
  /// @return true 如果有 vocabularySource
  public boolean hasVocabularySource() {
    return StrUtil.isNotBlank(vocabularySource);
  }

  /// 判断是否为期刊论文类型。
  ///
  /// @return true 如果类型值包含 "Journal Article"
  public boolean isJournalArticle() {
    return typeValue.toLowerCase().contains("journal article");
  }

  /// 判断是否为综述类型。
  ///
  /// @return true 如果类型值包含 "Review"
  public boolean isReview() {
    return typeValue.toLowerCase().contains("review");
  }
}
