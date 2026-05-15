package dev.linqibin.patra.catalog.infra.adapter.enrichment.scopus;

import dev.linqibin.patra.catalog.domain.port.enrichment.ScopusVenueData;
import dev.linqibin.patra.catalog.domain.port.enrichment.ScopusVenueData.YearlyMetric;
import dev.linqibin.patra.catalog.infra.persistence.entity.ScopusRatingEntity;
import dev.linqibin.starter.jpa.id.SnowflakeIdGenerator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/// Scopus 数据映射器。
///
/// 将 {@link ScopusVenueData}（端口 DTO）转换为 {@link ScopusRatingEntity} 列表。
///
/// **映射规则**：
///
/// - 最新年份（第一条 YearlyMetric）：填充 SJR/SNIP/CiteScoreTracker/分区/百分位等全部字段
/// - 历史年份：仅填充 CiteScore + 发文/引用统计
/// - 所有年份共享 `scopusSourceId` 和 `fetchedAt`
///
/// @author linqibin
/// @since 0.1.0
public class ScopusDataMapper {

  /// 将 ScopusVenueData 映射为 ScopusRatingEntity 列表。
  ///
  /// @param data Scopus API 返回的原始数据
  /// @param venueId 关联的 Venue ID
  /// @return 评级实体列表（按年份排序），data 为 null 或无历史数据时返回空列表
  public List<ScopusRatingEntity> mapToScopusRatings(ScopusVenueData data, Long venueId) {
    if (data == null || data.yearlyMetrics().isEmpty()) {
      return List.of();
    }

    Instant now = Instant.now();
    List<ScopusRatingEntity> entities = new ArrayList<>();
    boolean isFirst = true;

    for (YearlyMetric metric : data.yearlyMetrics()) {
      ScopusRatingEntity entity = new ScopusRatingEntity();
      entity.setId(SnowflakeIdGenerator.getId());
      entity.setVenueId(venueId);
      entity.setYear((short) metric.year());
      entity.setScopusSourceId(data.scopusSourceId());
      entity.setFetchedAt(now);

      // CiteScore + 发文/引用（每年都有）
      entity.setCiteScore(toBigDecimal(metric.citeScore()));
      entity.setDocumentCount(metric.documentCount());
      entity.setCitationCount(metric.citationCount());
      entity.setPercentCited(toBigDecimal(metric.percentCited()));

      // 最新年份额外填充 SJR/SNIP/Tracker/分区
      if (isFirst) {
        entity.setCiteScoreTracker(toBigDecimal(data.citeScoreTracker()));
        entity.setSjr(toBigDecimal(data.sjr()));
        entity.setSnip(toBigDecimal(data.snip()));
        entity.setSubjectArea(data.subjectArea());
        entity.setQuartile(data.quartile());
        entity.setPercentile(toBigDecimal(data.percentile()));
        isFirst = false;
      }

      entities.add(entity);
    }

    return entities;
  }

  /// 安全转换 Double 为 BigDecimal。
  private BigDecimal toBigDecimal(Double value) {
    return value != null ? BigDecimal.valueOf(value) : null;
  }
}
