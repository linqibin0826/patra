package dev.linqibin.patra.catalog.infra.persistence.entity;

import dev.linqibin.starter.jpa.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

/// 载体实例 JPA 实体，映射到表 `cat_venue_instance`。
///
/// **设计说明**：
///
/// - 继承 `BaseJpaEntity` 获得审计、乐观锁、软删除功能
/// - 通过 `venueId` 外键关联 VenueAggregate（逻辑外键，不用 `@ManyToOne`）
/// - 支持三种实例类型：期刊（volume/issue）、书籍（edition）、会议（conference*）
/// - 使用 Hibernate 7.1 的 `@JdbcTypeCode(SqlTypes.JSON)` 处理 JSON 字段
///
/// **索引设计**：
///
/// - `idx_venue_id`：载体 ID 索引
/// - `idx_venue_volume_issue`：期刊实例复合索引
/// - `idx_publication_year`：出版年份索引
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
    name = "cat_venue_instance",
    indexes = {
      @Index(name = "idx_venue_id", columnList = "venue_id"),
      @Index(name = "idx_venue_volume_issue", columnList = "venue_id, volume, issue"),
      @Index(name = "idx_publication_year", columnList = "publication_year")
    })
public class VenueInstanceEntity extends BaseJpaEntity {

  // ========== 关联引用 ==========

  /// 载体 ID（逻辑外键：cat_venue.id）
  @Column(name = "venue_id", nullable = false)
  private Long venueId;

  // ========== 期刊字段 ==========

  /// 卷号（如 "45", "2023"，期刊专用）
  @Column(name = "volume", length = 50)
  private String volume;

  /// 期号（如 "3", "Suppl 1"，期刊专用）
  @Column(name = "issue", length = 50)
  private String issue;

  // ========== 书籍字段 ==========

  /// 版次（如 "2nd Edition"，书籍专用）
  @Column(name = "edition", length = 100)
  private String edition;

  // ========== 出版日期 ==========

  /// 出版年份（必填，1800-2100）
  @Column(name = "publication_year", nullable = false)
  private Integer publicationYear;

  /// 出版月份（1-12，可选）
  @Column(name = "publication_month")
  private Integer publicationMonth;

  /// 出版日期（1-31，可选）
  @Column(name = "publication_day")
  private Integer publicationDay;

  // ========== 会议字段 ==========

  /// 会议名称（会议专用）
  @Column(name = "conference_name", length = 500)
  private String conferenceName;

  /// 会议开始日期（会议专用）
  @Column(name = "conference_start_date")
  private LocalDate conferenceStartDate;

  /// 会议结束日期（会议专用）
  @Column(name = "conference_end_date")
  private LocalDate conferenceEndDate;

  /// 会议地点（会议专用）
  @Column(name = "conference_location", length = 500)
  private String conferenceLocation;

  // ========== 扩展字段 ==========

  /// 实例元数据（JSON 格式，灵活扩展）
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "instance_metadata", columnDefinition = "JSON")
  private JsonNode instanceMetadata;
}
