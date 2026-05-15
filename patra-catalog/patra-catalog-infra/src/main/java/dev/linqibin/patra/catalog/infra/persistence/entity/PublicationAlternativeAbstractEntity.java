package dev.linqibin.patra.catalog.infra.persistence.entity;

import com.patra.starter.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/// 文献翻译摘要 JPA 实体，映射到表 `cat_publication_alternative_abstract`。
///
/// **表结构**：管理文献摘要的多语言版本（官方翻译、专业翻译、机器翻译）。
///
/// **关键字段说明**：
///
/// - `publication_id` 文献 ID，外键关联 cat_publication.id
/// - `language_code` 语言代码（ISO 639-1，如 "zh-CN"、"ja"）
/// - `source_type` 摘要来源类型（如 `publisher`、`plain-language-summary`）
/// - `translation_type` 翻译类型：Official/Professional/Machine/Community
/// - `is_official` 是否官方翻译
///
/// **索引说明**：
///
/// - 复合唯一索引 `uk_abstract_lang_source`: (publication_id, language_code, source_type)
///   保证每种语言下的同来源类型只有一个翻译
/// - 普通索引支持按文献、语言查询
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(
    name = "cat_publication_alternative_abstract",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_abstract_lang_source",
          columnNames = {"publication_id", "language_code", "source_type"})
    },
    indexes = {
      @Index(name = "idx_publication", columnList = "publication_id"),
      @Index(name = "idx_abstract", columnList = "abstract_id"),
      @Index(name = "idx_language", columnList = "language_code")
    })
public class PublicationAlternativeAbstractEntity extends ValueObjectJpaEntity {

  /// 文献 ID（外键：cat_publication.id）
  @Column(name = "publication_id", nullable = false)
  private Long publicationId;

  /// 主摘要 ID（外键：cat_publication_abstract.id）
  @Column(name = "abstract_id")
  private Long abstractId;

  /// 语言代码（ISO 639-1，如 "zh-CN"、"ja"）
  @Column(name = "language_code", nullable = false, length = 10)
  private String languageCode;

  /// 摘要来源类型（如 `publisher`、`plain-language-summary`）
  @Column(name = "source_type", nullable = false, length = 64)
  private String sourceType;

  /// 语言名称（如 "Chinese"、"Japanese"）
  @Column(name = "language_name", length = 50)
  private String languageName;

  /// 纯文本摘要
  @Lob
  @Column(name = "plain_text", columnDefinition = "TEXT")
  private String plainText;

  /// 结构化摘要段落（JSON 对象）
  @Column(name = "structured_sections", columnDefinition = "JSON")
  private String structuredSections;

  /// 翻译类型：Official/Professional/Machine/Community
  @Column(name = "translation_type", length = 50)
  private String translationType;

  /// 译者姓名或机构
  @Column(name = "translator", length = 100)
  private String translator;

  /// 翻译日期
  @Column(name = "translation_date")
  private LocalDate translationDate;

  /// 质量级别：Excellent/Good/Fair/Poor
  @Column(name = "quality_level", length = 50)
  private String qualityLevel;

  /// 是否官方翻译
  @Column(name = "is_official", nullable = false)
  private Boolean isOfficial = false;

  /// 顺序号
  @Column(name = "order_num")
  private Integer orderNum;
}
