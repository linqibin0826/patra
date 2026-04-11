package com.patra.catalog.infra.batch.venue.letpub;

import com.patra.catalog.infra.persistence.entity.CasRatingEntity;
import com.patra.catalog.infra.persistence.entity.JcrRatingEntity;
import java.util.List;

/// Processor → Writer 的中间传输记录。
///
/// **携带字段**：
///
/// 1. `venueId` — 目标 Venue 的数据库 ID
/// 2. `imageObjectKey` — 新下载的封面对象键（可空，为空表示本轮未下载或下载失败）
/// 3. `jcrRatings` — JCR 评级实体列表（每年一行，不可变）
/// 4. `casRatings` — CAS 评级实体列表（每个版本一行，不可变）
///
/// **为何 imageObjectKey 需要独立字段**：
///
/// `JpaPagingItemReader` 会在每页读完后将实体 detach，Processor 拿到的
/// `VenueEntity` 已脱离持久化上下文，直接 `setImageObjectKey(...)` 无法
/// 通过 dirty check 持久化。因此必须由 Writer 显式调用
/// `VenueDao.updateImageObjectKey` 完成持久化，Result 负责把值带到 Writer。
///
/// @param venueId 目标 Venue 的数据库 ID
/// @param imageObjectKey 新下载的封面对象键，为空表示不更新 `cat_venue.image_object_key`
/// @param jcrRatings JCR 评级实体列表（每年一行，不可变）
/// @param casRatings CAS 评级实体列表（每个版本一行，不可变）
/// @author linqibin
/// @since 0.1.0
public record LetPubEnrichResult(
    Long venueId,
    String imageObjectKey,
    List<JcrRatingEntity> jcrRatings,
    List<CasRatingEntity> casRatings) {

  /// 紧凑构造器：对集合字段进行防御性拷贝。
  public LetPubEnrichResult {
    jcrRatings = jcrRatings != null ? List.copyOf(jcrRatings) : List.of();
    casRatings = casRatings != null ? List.copyOf(casRatings) : List.of();
  }

  /// 创建 LetPubEnrichResult 实例。
  public static LetPubEnrichResult of(
      Long venueId,
      String imageObjectKey,
      List<JcrRatingEntity> jcrRatings,
      List<CasRatingEntity> casRatings) {
    return new LetPubEnrichResult(venueId, imageObjectKey, jcrRatings, casRatings);
  }
}
