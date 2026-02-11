package com.patra.catalog.infra.adapter.persistence.entity;

import com.patra.starter.jpa.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.Locale;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/// 关键词 JPA 实体，映射到表 `cat_keyword`。
///
/// **设计说明**：
///
/// - 继承 `BaseJpaEntity`，作为独立聚合根管理
/// - 支持规范化去重和频次统计
/// - 通过 `normalized_term` 实现同义词合并
///
/// **业务含义**：
///
/// 关键词用于描述文章的核心主题，帮助检索和分类。来源包括：
/// - author（作者提供的关键词）
/// - editor（编辑添加的关键词）
/// - indexer（索引机构添加的关键词）
/// - pubmed（PubMed 系统添加）
///
/// **索引设计**：
///
/// - `idx_normalized`：规范化词形索引（去重查询）
/// - `idx_frequency`：频次索引（热门关键词排序）
/// - `idx_source_lang`：来源+语言复合索引
///
/// @author linqibin
/// @since 0.1.0
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "cat_keyword",
    indexes = {
      @Index(name = "idx_frequency", columnList = "frequency"),
      @Index(name = "idx_source_lang", columnList = "source, language")
    },
    uniqueConstraints = {
      @jakarta.persistence.UniqueConstraint(
          name = "uk_normalized_term",
          columnNames = "normalized_term")
    })
public class KeywordEntity extends BaseJpaEntity {

  private static final int TERM_MAX_LENGTH = 500;

  private static final int NORMALIZED_TERM_MAX_LENGTH = 500;

  // ========== 关键词信息 ==========

  /// 关键词原始形式。
  @Column(name = "term", nullable = false, length = TERM_MAX_LENGTH)
  private String term;

  /// 关键词来源。
  ///
  /// 枚举值：author/editor/indexer/pubmed。
  @Column(name = "source", length = 50)
  private String source;

  /// 语言代码（ISO 639-1）。
  ///
  /// 如 en、zh 等。
  @Column(name = "language", length = 10)
  private String language;

  /// 规范化词形。
  ///
  /// 小写 + 去标点 + 去空格，用于去重。
  @Column(name = "normalized_term", length = NORMALIZED_TERM_MAX_LENGTH)
  private String normalizedTerm;

  /// 出现频次。
  ///
  /// 被多少篇文献使用。
  @Column(name = "frequency")
  @lombok.Builder.Default
  private Integer frequency = 0;

  // ========== 便捷方法 ==========

  /// 创建关键词记录。
  ///
  /// @param term 关键词原始形式
  /// @param source 来源
  /// @param language 语言代码
  /// @return 新建的实体
  public static KeywordEntity of(String term, String source, String language) {
    return KeywordEntity.builder()
        .term(term)
        .source(source)
        .language(language)
        .normalizedTerm(normalize(term))
        .frequency(0)
        .build();
  }

  /// 规范化关键词。
  ///
  /// **规范化规则**：
  /// - 转换为小写
  /// - 保留所有语言的字母（`\p{L}`）和数字（`\p{N}`）
  /// - 移除标点、空格等非字母数字字符
  ///
  /// **Unicode 字符类说明**：
  /// - `\p{L}` 匹配任意语言的字母（英文、中文、日文、韩文、阿拉伯文等）
  /// - `\p{N}` 匹配任意数字（阿拉伯数字、罗马数字等）
  ///
  /// @param term 原始关键词
  /// @return 规范化后的关键词（小写 + 去标点 + 去空格）
  public static String normalize(String term) {
    if (term == null) {
      return null;
    }
    String normalized = term.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    if (normalized.codePointCount(0, normalized.length()) <= NORMALIZED_TERM_MAX_LENGTH) {
      return normalized;
    }
    int endIndex = normalized.offsetByCodePoints(0, NORMALIZED_TERM_MAX_LENGTH);
    return normalized.substring(0, endIndex);
  }
}
