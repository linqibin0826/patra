package com.patra.catalog.infra.batch.venue.letpub;

import com.patra.catalog.infra.persistence.entity.CasRatingEntity;
import com.patra.catalog.infra.persistence.entity.CasWarningEntity;
import com.patra.catalog.infra.persistence.entity.JcrRatingEntity;
import java.util.List;

/// Processor → Writer 的中间传输记录。
///
/// **携带字段**：
///
/// 1. `venueId` — 目标 Venue 的数据库 ID
/// 2. `imageObjectKey` — 新下载的封面对象键（可空，为空表示本轮未下载或下载失败）
/// 3. `jcr` — JCR 相关批次（包含 JCR 评级等 WOS/Clarivate 体系下的数据）
/// 4. `cas` — CAS 相关批次（包含 CAS 分区评级等中科院体系下的数据）
///
/// **按评级体系分组的设计动机**：
///
/// 每个评级体系（JCR/CAS）有独立的数据演化路径。按体系分组后：
///
/// - 顶层 record 稳定在 4 个参数（使用 `of()` 工厂方法而非 `@Builder`）
/// - 未来为 CAS 新增字段（如预警名单、分区趋势历史）只在 `CasBatch` 内扩展
/// - 未来为 JCR 新增字段（如 JIF 百分位、h-index）只在 `JcrBatch` 内扩展
/// - Writer 可以按体系独立处理各自的批次，职责清晰
///
/// **为何 imageObjectKey 需要独立字段**（保留 Task 7 的设计）：
///
/// `JpaPagingItemReader` 会在每页读完后将实体 detach，Processor 拿到的
/// `VenueEntity` 已脱离持久化上下文，直接 `setImageObjectKey(...)` 无法
/// 通过 dirty check 持久化。因此必须由 Writer 显式调用
/// `VenueDao.updateImageObjectKey` 完成持久化，Result 负责把值带到 Writer。
///
/// @param venueId 目标 Venue 的数据库 ID
/// @param imageObjectKey 新下载的封面对象键，为空表示不更新 `cat_venue.image_object_key`
/// @param jcr JCR 批次（WOS/Clarivate 体系）
/// @param cas CAS 批次（中科院体系）
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

  /// JCR 相关批次。
  ///
  /// **当前字段**：
  ///
  /// - `ratings` — JCR 评级实体列表（每年一行，最新年附加详情）
  ///
  /// **未来扩展（按评级体系分组的价值所在）**：
  ///
  /// - JIF / JCI 百分位
  /// - h-index / 实时影响因子 / 自引率 等独立指标
  ///
  /// @param ratings JCR 评级实体列表（不可变）
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

  /// CAS 相关批次。
  ///
  /// **字段**：
  ///
  /// - `ratings` — CAS 分区评级实体列表（每个版本一行）
  /// - `warnings` — CAS 预警名单时间序列（独立于分区表的时间线）
  ///
  /// **为何 ratings 与 warnings 并列而非合并**：
  ///
  /// 虽然两者都来自 LetPub 的 CAS 数据，但它们是两个**独立的时间序列**：
  /// 发布节奏不同（预警年初发布，分区可能春秋两季），版本命名风格不同
  /// （预警用 `2025版`，分区用 `升级版`），历史覆盖不同（预警覆盖 2020+，
  /// 分区只展示近几年）。强制按 year 对齐会产生大量 NULL 行。
  ///
  /// @param ratings CAS 分区评级实体列表（不可变）
  /// @param warnings CAS 预警记录实体列表（不可变）
  public record CasBatch(List<CasRatingEntity> ratings, List<CasWarningEntity> warnings) {

    public CasBatch {
      ratings = ratings != null ? List.copyOf(ratings) : List.of();
      warnings = warnings != null ? List.copyOf(warnings) : List.of();
    }

    /// 创建空批次。
    public static CasBatch empty() {
      return new CasBatch(List.of(), List.of());
    }

    /// 仅包含评级的批次（无预警）。
    public static CasBatch of(List<CasRatingEntity> ratings) {
      return new CasBatch(ratings, List.of());
    }

    /// 同时包含评级和预警的批次。
    public static CasBatch of(List<CasRatingEntity> ratings, List<CasWarningEntity> warnings) {
      return new CasBatch(ratings, warnings);
    }
  }
}
