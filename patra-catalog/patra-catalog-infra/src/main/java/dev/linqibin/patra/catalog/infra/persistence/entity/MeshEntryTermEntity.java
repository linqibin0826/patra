package dev.linqibin.patra.catalog.infra.persistence.entity;

import dev.linqibin.patra.catalog.domain.model.enums.MeshRecordType;
import com.patra.starter.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
/// **表结构**：存储 MeSH 主题词和 SCR 的同义词和入口术语，支持模糊检索。
///
/// **数据规模**：Descriptor 约 25 万条 + SCR 约 320 万条
///
/// **关键字段说明**：
///
/// - `record_type` 记录类型（DESCRIPTOR=主题词，SCR=补充概念）
/// - `owner_ui` 所有者 UI（Descriptor: D开头，SCR: C开头）
/// - `term_ui` 术语 UI
/// - `concept_ui` 所属概念 UI
/// - `term` 入口术语/同义词
/// - `lexical_tag` 词法标记（NON/PEF/LAB/ABB/ACR/NAM）
/// - `is_concept_preferred` 是否概念首选术语
///
/// **索引说明**：
///
/// - 复合索引 `idx_record_type_owner`: 支持按类型和所有者查询
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
      @Index(name = "idx_record_type_owner", columnList = "record_type, owner_ui"),
      @Index(name = "idx_concept_ui", columnList = "concept_ui")
    })
public class MeshEntryTermEntity extends ValueObjectJpaEntity {

  /// 记录类型（DESCRIPTOR=主题词，SCR=补充概念）
  @Enumerated(EnumType.STRING)
  @Column(name = "record_type", nullable = false, length = 20)
  private MeshRecordType recordType;

  /// 所有者 UI（Descriptor: D开头，SCR: C开头）
  @Column(name = "owner_ui", nullable = false, length = 10)
  private String ownerUi;

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
