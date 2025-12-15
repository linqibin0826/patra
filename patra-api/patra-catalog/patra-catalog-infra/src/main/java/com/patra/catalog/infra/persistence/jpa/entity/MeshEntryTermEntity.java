package com.patra.catalog.infra.persistence.jpa.entity;

import com.patra.starter.jpa.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/// MeSH 入口术语 JPA 实体，映射到表 `cat_mesh_entry_term`。
///
/// **表结构**：存储 MeSH 主题词的同义词和入口术语，支持模糊检索。
///
/// **数据规模**：约 25 万条（一个主题词平均 7-8 个入口术语）
///
/// **关键字段说明**：
///
/// - `descriptor_ui` 主题词 UI（关联 cat_mesh_descriptor.ui）
/// - `term_ui` 术语 UI
/// - `concept_ui` 所属概念 UI
/// - `term` 入口术语/同义词
/// - `lexical_tag` 词法标记（NON/PEF/LAB/ABB/ACR/NAM）
/// - `is_concept_preferred` 是否概念首选术语
///
/// **索引说明**：
///
/// - 普通索引 `idx_descriptor_ui`: 支持查询某主题词的所有入口术语
/// - 普通索引 `idx_concept_ui`: 支持按概念查询入口术语
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(
    name = "cat_mesh_entry_term",
    indexes = {
      @Index(name = "idx_descriptor_ui", columnList = "descriptor_ui"),
      @Index(name = "idx_concept_ui", columnList = "concept_ui")
    })
public class MeshEntryTermEntity extends BaseJpaEntity {

  /// 主题词 UI（关联：cat_mesh_descriptor.ui）
  @Column(name = "descriptor_ui", nullable = false, length = 10)
  private String descriptorUi;

  /// 术语 UI
  @Column(name = "term_ui", length = 10)
  private String termUi;

  /// 所属概念 UI
  @Column(name = "concept_ui", length = 10)
  private String conceptUi;

  /// 入口术语/同义词
  @Column(name = "term", nullable = false, length = 255)
  private String term;

  /// 词法标记（枚举：NON/PEF/LAB/ABB/ACR/NAM）
  @Column(name = "lexical_tag", length = 10)
  private String lexicalTag;

  /// 是否打印（false=否，true=是）
  @Column(name = "is_print_flag", nullable = false)
  private Boolean isPrintFlag;

  /// 记录首选（枚举：Y/N）
  @Column(name = "record_preferred", length = 10)
  private String recordPreferred;

  /// 是否排列术语（false=否，true=是）
  @Column(name = "is_permuted_term", nullable = false)
  private Boolean isPermutedTerm;

  /// 是否概念首选术语（false=否，true=是）
  @Column(name = "is_concept_preferred", nullable = false)
  private Boolean isConceptPreferred;

  /// 术语缩写
  @Column(name = "abbreviation", length = 50)
  private String abbreviation;

  /// 排序版本
  @Column(name = "sort_version", length = 255)
  private String sortVersion;

  /// 入口版本
  @Column(name = "entry_version", length = 100)
  private String entryVersion;

  /// 术语说明
  @Column(name = "term_note", columnDefinition = "TEXT")
  private String termNote;

  /// 创建日期
  @Column(name = "date_created")
  private LocalDate dateCreated;

  /// 来源词库 ID 列表（JSON 数组）
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "thesaurus_ids", columnDefinition = "JSON")
  private List<String> thesaurusIds;
}
