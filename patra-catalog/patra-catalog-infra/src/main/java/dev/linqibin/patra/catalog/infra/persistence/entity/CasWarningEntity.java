package dev.linqibin.patra.catalog.infra.persistence.entity;

import dev.linqibin.patra.catalog.domain.model.enums.CasWarningLevel;
import dev.linqibin.patra.catalog.infra.persistence.converter.attribute.CasWarningLevelConverter;
import dev.linqibin.starter.jpa.entity.ChildJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/// CAS 中科院期刊预警名单 JPA 实体，映射到表 `cat_venue_cas_warning`。
///
/// **数据模型**：CAS 预警名单按版本的历史时间序列。
///
/// **唯一约束**：`(venue_id, published_year, edition_label)` — 每个期刊每个发布版本至多一条记录。
///
/// **为何独立于 `cat_venue_cas_rating`**：
///
/// 预警名单和 CAS 分区表是两个独立的数据发布：
///
/// - 发布节奏不同：预警名单通常年初发布（1-3 月），分区表可能 3 月或 12 月
/// - 版本命名风格不同：预警名单用 `2025版`/`新锐学术版`，分区表用 `升级版`/`新锐版`
/// - 历史覆盖不同：预警名单覆盖 2020+，分区表只展示近几年版本
/// - 按 venue 对齐时分区表和预警名单数量经常不同（比如 Nature 有 3 条分区 + 6 条预警）
///
/// **数据来源**：通过 LetPub 爬取填充，由 `LetPubDataMapper.mapToCasWarnings` 生成。
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(
    name = "cat_venue_cas_warning",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_venue_year_edition",
          columnNames = {"venue_id", "published_year", "edition_label"})
    },
    indexes = {
      @Index(name = "idx_venue_id", columnList = "venue_id"),
      @Index(name = "idx_in_warning", columnList = "in_warning_list"),
      @Index(name = "idx_warning_level", columnList = "warning_level"),
      @Index(name = "idx_published_year", columnList = "published_year")
    })
public class CasWarningEntity extends ChildJpaEntity {

  /// 关联期刊 ID（逻辑外键 → cat_venue.id）
  @Column(name = "venue_id", nullable = false)
  private Long venueId;

  /// 预警名单发布年份（2020-2100）
  @Column(name = "published_year", nullable = false)
  private Short publishedYear;

  /// 预警名单发布月份 1-12（可空）
  @Column(name = "published_month")
  private Short publishedMonth;

  /// 原始版本标签：新锐学术版 / 2025版 / 2024版 / 2023版 等
  @Column(name = "edition_label", nullable = false, length = 30)
  private String editionLabel;

  /// 是否在预警名单中：true=预警，false=正常
  @Column(name = "in_warning_list", nullable = false)
  private Boolean inWarningList;

  /// 预警级别（HIGH/MEDIUM/LOW，仅当 inWarningList=true 时可能有值）。
  /// 持久化为小写英文代码（`high`/`medium`/`low`）。
  @Convert(converter = CasWarningLevelConverter.class)
  @Column(name = "warning_level", length = 10)
  private CasWarningLevel warningLevel;

  /// 原始描述文本（保留 LetPub 页面原句以便追溯）
  @Column(name = "raw_text", length = 500)
  private String rawText;

  /// 数据来源 URL
  @Column(name = "source_url", length = 500)
  private String sourceUrl;

  /// 数据抓取时间（UTC，微秒精度）
  @Column(name = "fetched_at")
  private Instant fetchedAt;
}
