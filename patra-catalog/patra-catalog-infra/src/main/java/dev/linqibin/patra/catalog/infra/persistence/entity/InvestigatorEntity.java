package dev.linqibin.patra.catalog.infra.persistence.entity;

import dev.linqibin.starter.jpa.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

/// 研究者 JPA 实体，映射到表 `cat_investigator`。
///
/// **设计说明**：
///
/// - 继承 `BaseJpaEntity` 支持完整审计（独立实体）
/// - 存储研究者信息（非作者的研究人员，如临床试验 PI）
/// - 支持通过 ORCID 或 dedupKey 进行去重匹配
///
/// **去重策略**：
///
/// 1. **ORCID 精确匹配**：全局唯一标识，优先级最高
/// 2. **dedupKey 匹配**：MD5(LOWER(lastName) + "|" + LOWER(foreName) + "|" + LOWER(COALESCE(orcid,
// "")))
/// 3. **无匹配时创建新记录**
///
/// **索引设计**：
///
/// - `idx_orcid`：ORCID 索引，支持按 ORCID 查询研究者
/// - `idx_dedup_key`：去重键索引，支持研究者去重和合并
/// - `idx_email`：邮箱索引，支持按邮箱查询研究者
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
    name = "cat_investigator",
    indexes = {
      @Index(name = "idx_orcid", columnList = "orcid"),
      @Index(name = "idx_dedup_key", columnList = "dedup_key"),
      @Index(name = "idx_email", columnList = "email")
    })
public class InvestigatorEntity extends BaseJpaEntity {

  // ========== 姓名信息 ==========

  /// 姓（Last Name/Family Name）。
  @Column(name = "last_name", length = 200)
  private String lastName;

  /// 名（First Name/Given Name）。
  @Column(name = "fore_name", length = 200)
  private String foreName;

  /// 姓名缩写（如 "J.K."）。
  @Column(name = "initials", length = 50)
  private String initials;

  /// 后缀（如 "Jr.", "III", "MD", "PhD"）。
  @Column(name = "suffix", length = 50)
  private String suffix;

  // ========== 标识符 ==========

  /// ORCID 标识符（格式: 0000-0001-2345-6789）。
  ///
  /// 全局唯一标识，用于研究者精确匹配。
  @Column(name = "orcid", length = 50)
  private String orcid;

  /// 研究者 ID（ResearcherID/Publons）。
  @Column(name = "researcher_id", length = 100)
  private String researcherId;

  // ========== 角色与机构 ==========

  /// 研究者类型（如 "PI", "CoI", "Collaborator"）。
  @Column(name = "investigator_type", length = 100)
  private String investigatorType;

  /// 机构名称（文本，不关联 organization 表）。
  @Column(name = "affiliation_name", length = 500)
  private String affiliationName;

  /// 邮箱地址。
  @Column(name = "email", length = 255)
  private String email;

  // ========== 去重与扩展 ==========

  /// 复合去重键（应用层计算，MD5 哈希）。
  ///
  /// 计算规则：MD5(LOWER(lastName) + "|" + LOWER(foreName) + "|" + LOWER(COALESCE(orcid, "")))
  @Column(name = "dedup_key", length = 255)
  private String dedupKey;

  /// 研究者元数据（JSON 格式，灵活扩展）。
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata", columnDefinition = "JSON")
  private JsonNode metadata;
}
