package com.patra.catalog.infra.persistence.entity;

import com.patra.catalog.domain.model.enums.RatingSystem;
import com.patra.starter.jpa.entity.ChildJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

/// 载体评级 JPA 实体，映射到表 `cat_venue_rating`。
///
/// **设计说明**：
///
/// - 继承 `BaseJpaEntity` 获得审计、乐观锁、软删除功能
/// - 业务唯一键：`(venue_id, year, rating_system)` 由唯一约束保证
/// - 使用 Hibernate 7.1 的 `@JdbcTypeCode(SqlTypes.JSON)` 处理 JSON 字段
/// - `RatingSystem` 枚举通过 `RatingSystemAttributeConverter` 自动转换
///
/// **索引设计**：
///
/// - `uk_venue_year_system`：业务唯一约束
/// - `idx_venue_id`：载体 ID 索引
/// - `idx_rating_system`：评价体系索引
/// - `idx_year`：年份索引
/// - `idx_quartile`：分区索引
/// - `idx_impact_score`：影响力分数索引
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
    name = "cat_venue_rating",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_venue_year_system",
          columnNames = {"venue_id", "year", "rating_system"})
    },
    indexes = {
      @Index(name = "idx_venue_id", columnList = "venue_id"),
      @Index(name = "idx_rating_system", columnList = "rating_system"),
      @Index(name = "idx_year", columnList = "year"),
      @Index(name = "idx_quartile", columnList = "quartile"),
      @Index(name = "idx_impact_score", columnList = "impact_score")
    })
public class VenueRatingEntity extends ChildJpaEntity {

  // ========== 核心属性（不变量） ==========

  /// 载体 ID（逻辑外键：cat_venue.id）
  @Column(name = "venue_id", nullable = false)
  private Long venueId;

  /// 评级年份（2000-2100）
  @Column(name = "year", nullable = false)
  private Short year;

  /// 评价体系：JCR/CAS/SCOPUS（通过 RatingSystemAttributeConverter 自动转换）
  @Column(name = "rating_system", nullable = false, length = 32)
  private RatingSystem ratingSystem;

  // ========== 评级数据 ==========

  /// 分区（JCR: Q1-Q4，CAS: 1区-4区）
  @Column(name = "quartile", length = 10)
  private String quartile;

  /// 影响力分数（JCR Impact Factor / SCOPUS CiteScore / CAS 复合IF）
  @Column(name = "impact_score", precision = 10, scale = 4)
  private BigDecimal impactScore;

  /// 评级详情（JSON，各体系特有字段）
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "rating_data", columnDefinition = "JSON")
  private JsonNode ratingData;

  /// 学科分类及分区（JSON 数组）
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "categories", columnDefinition = "JSON")
  private JsonNode categories;

  // ========== 数据来源 ==========

  /// 数据来源 URL
  @Column(name = "source_url", length = 500)
  private String sourceUrl;

  /// 数据获取时间
  @Column(name = "fetched_at")
  private Instant fetchedAt;
}
