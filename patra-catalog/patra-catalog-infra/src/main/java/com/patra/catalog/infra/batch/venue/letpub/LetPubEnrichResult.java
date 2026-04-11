package com.patra.catalog.infra.batch.venue.letpub;

import com.patra.catalog.infra.persistence.entity.CasRatingEntity;
import com.patra.catalog.infra.persistence.entity.CasWarningEntity;
import com.patra.catalog.infra.persistence.entity.JcrRatingEntity;
import java.util.List;

/// Processor → Writer 的中间传输记录，按评级体系（JCR/CAS）分组持有待写入的实体批次。
///
/// **为何 imageObjectKey 需要独立字段**：
///
/// `JpaPagingItemReader` 读出的 `VenueEntity` 已 detached，直接 setter 无法被 dirty check 捕获；
/// Writer 必须显式 `UPDATE cat_venue`，Result 负责把新下载的对象键带过去。
///
/// @param venueId 目标 Venue 的数据库 ID
/// @param imageObjectKey 新下载的封面对象键，为空表示不更新 `cat_venue.image_object_key`
/// @param jcr JCR 批次（WOS/Clarivate 体系）
/// @param cas CAS 批次（中科院体系，含分区评级和预警名单两条独立时间序列）
/// @author linqibin
/// @since 0.1.0
public record LetPubEnrichResult(Long venueId, String imageObjectKey, JcrBatch jcr, CasBatch cas) {

  /// 紧凑构造器：对 sub-record 字段做 null 归一化。
  public LetPubEnrichResult {
    jcr = jcr != null ? jcr : JcrBatch.empty();
    cas = cas != null ? cas : CasBatch.empty();
  }

  /// 创建 LetPubEnrichResult 实例。
  public static LetPubEnrichResult of(
      Long venueId, String imageObjectKey, JcrBatch jcr, CasBatch cas) {
    return new LetPubEnrichResult(venueId, imageObjectKey, jcr, cas);
  }

  /// JCR 评级批次。
  ///
  /// @param ratings JCR 评级实体列表（不可变，每年一行；详细分区/排名/百分位等 Clarivate 年度指标
  ///     受 LetPub 数据源限制仅最新年可填，未来接入一级源后可回填历史年）
  public record JcrBatch(List<JcrRatingEntity> ratings) {

    public JcrBatch {
      ratings = ratings != null ? List.copyOf(ratings) : List.of();
    }

    /// 创建空批次。
    public static JcrBatch empty() {
      return new JcrBatch(List.of());
    }

    /// 使用指定评级列表创建批次。
    public static JcrBatch of(List<JcrRatingEntity> ratings) {
      return new JcrBatch(ratings);
    }
  }

  /// CAS 批次。
  ///
  /// `ratings` 与 `warnings` 并列而非合并：两者是独立的时间序列（发布节奏、版本命名、
  /// 历史覆盖均不同步），按 year 对齐会产生大量 NULL 行。
  ///
  /// @param ratings CAS 分区评级实体列表（不可变，每个版本一行）
  /// @param warnings CAS 预警记录实体列表（不可变，时间序列）
  public record CasBatch(List<CasRatingEntity> ratings, List<CasWarningEntity> warnings) {

    public CasBatch {
      ratings = ratings != null ? List.copyOf(ratings) : List.of();
      warnings = warnings != null ? List.copyOf(warnings) : List.of();
    }

    /// 创建空批次。
    public static CasBatch empty() {
      return new CasBatch(List.of(), List.of());
    }

    /// 同时包含评级和预警的批次。
    public static CasBatch of(List<CasRatingEntity> ratings, List<CasWarningEntity> warnings) {
      return new CasBatch(ratings, warnings);
    }
  }
}
