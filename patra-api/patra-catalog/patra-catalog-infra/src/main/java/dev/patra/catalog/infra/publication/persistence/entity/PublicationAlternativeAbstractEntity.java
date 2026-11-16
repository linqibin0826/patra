package dev.patra.catalog.infra.publication.persistence.entity;

import dev.patra.catalog.domain.publication.model.vo.PublicationAbstractSection;
import dev.patra.tool.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(name = "cat_publication_alternative_abstract")
public class PublicationAlternativeAbstractEntity extends ValueObjectJpaEntity {

  /// 文献 ID（引用 cat_publication.id，不建外键）
  @Column(name = "publication_id", nullable = false)
  private Long publicationId;

  /// 主摘要 ID（引用 cat_publication_abstract.id，不建外键）
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
  @Column(name = "plain_text", columnDefinition = "TEXT")
  private String plainText;

  /// 结构化摘要段落（有序数组 `[{"label","text"}]`；纯文本摘要为 null）
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "structured_sections", columnDefinition = "jsonb")
  private List<PublicationAbstractSection> structuredSections;

  /// 版权信息
  @Column(name = "copyright", length = 1000)
  private String copyright;

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
