package dev.patra.catalog.infra.venue.persistence.entity;

import dev.patra.tool.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/// 载体关联关系 JPA 实体，映射到表 `cat_venue_relation`。
///
/// **表结构**：存储期刊之间的历史关系（前身、后继、合并、拆分等），来源于 NLM Serfile。
///
/// **关键字段说明**：
///
/// - `venue_id` 载体 ID，外键关联 cat_venue.id
/// - `related_venue_id` 关联期刊 ID（如果已在系统中）
/// - `related_nlm_id` 关联期刊的 NLM ID
/// - `related_title` 关联期刊标题（必填）
/// - `relation_type` 关系类型枚举：PRECEDING/SUCCEEDING/ABSORBED/MERGED 等
/// - `effective_date` 关系生效日期
///
/// **使用场景**：
///
/// 1. 从 Serfile 导入期刊的 TitleRelated 数据
/// 2. 追溯期刊的历史演变（合并、拆分、更名）
/// 3. 关联引用数据统计（合并前后期刊的被引用统计）
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(name = "cat_venue_relation")
public class VenueRelationEntity extends ValueObjectJpaEntity {

  /// 载体 ID（引用 cat_venue.id，不建外键）
  @Column(name = "venue_id", nullable = false)
  private Long venueId;

  /// 关联期刊 ID（如果已在系统中）
  @Column(name = "related_venue_id")
  private Long relatedVenueId;

  /// 关联期刊 NLM ID
  @Column(name = "related_nlm_id", length = 20)
  private String relatedNlmId;

  /// 关联期刊标题
  @Column(name = "related_title", nullable = false, length = 500)
  private String relatedTitle;

  /// 关系类型：PRECEDING/SUCCEEDING/ABSORBED/ABSORBED_BY/MERGED/SPLIT_FROM 等
  @Column(name = "relation_type", nullable = false, length = 50)
  private String relationType;

  /// 生效日期
  @Column(name = "effective_date")
  private LocalDate effectiveDate;

  /// 备注说明
  @Column(name = "notes", length = 1000)
  private String notes;
}
