package com.patra.catalog.infra.batch.venue.letpub;

import com.patra.catalog.domain.port.enrichment.LetPubVenueData;
import com.patra.catalog.domain.port.enrichment.LetPubVenueData.CasPartition;
import com.patra.catalog.domain.port.enrichment.LetPubVenueData.CasWarningRecord;
import com.patra.catalog.infra.persistence.entity.CasRatingEntity;
import com.patra.catalog.infra.persistence.entity.CasWarningEntity;
import com.patra.catalog.infra.persistence.entity.JcrRatingEntity;
import com.patra.starter.jpa.id.SnowflakeIdGenerator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/// LetPub 原始数据拆解映射器。
///
/// 将 {@link LetPubVenueData}（端口级中间 DTO）拆解为强类型实体：
///
/// - IF 趋势 + JCR 分区 → 多行 `JcrRatingEntity`（每年一行）
/// - CAS 分区 → 多行 `CasRatingEntity`（每个版本一行，如新锐版/升级版/旧版）
///
/// 不依赖 JSON 序列化，所有字段直接映射到实体的强类型列。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class LetPubDataMapper {

  /// 年份提取正则：从 "2024-2025" 提取后半段 2025。
  private static final Pattern YEAR_RANGE_PATTERN = Pattern.compile("(\\d{4})-(\\d{4})");

  /// CAS 版本年份提取正则：从 "2025年3月升级版" 提取 2025。
  private static final Pattern CAS_VERSION_YEAR_PATTERN = Pattern.compile("(20\\d{2})年");

  /// CAS 版本名称提取正则：从 "2025年3月升级版" 提取 "升级版"。
  private static final Pattern CAS_EDITION_PATTERN = Pattern.compile("(升级版|新锐版|基础版)$");

  /// 将 LetPub IF 趋势和 JCR 分区数据映射为多行 JCR 评级。
  ///
  /// - 每个 IF 趋势条目生成一行（year = key 后半段）
  /// - 最新年份的行额外填充 quartile、rank、subject、collection 等详情
  ///
  /// @param data LetPub 原始数据
  /// @param venueId 目标 venue ID
  /// @param sourceUrl LetPub 详情页 URL（数据溯源）
  /// @return JCR 评级实体列表（可能为空）
  public List<JcrRatingEntity> mapToJcrRatings(
      LetPubVenueData data, Long venueId, String sourceUrl) {
    Map<String, Double> trend = data.impactFactorTrend();
    if (trend == null || trend.isEmpty()) {
      return List.of();
    }

    Instant now = Instant.now();

    // 找出最新年份
    short latestYear =
        trend.keySet().stream()
            .map(this::extractLaterYear)
            .filter(y -> y > 0)
            .max(Comparator.naturalOrder())
            .orElse((short) 0);

    List<JcrRatingEntity> ratings = new ArrayList<>();
    for (Map.Entry<String, Double> entry : trend.entrySet()) {
      short year = extractLaterYear(entry.getKey());
      if (year <= 0) {
        continue;
      }

      var entity = new JcrRatingEntity();
      entity.setId(SnowflakeIdGenerator.getId());
      entity.setVenueId(venueId);
      entity.setYear(year);
      entity.setImpactFactor(BigDecimal.valueOf(entry.getValue()));
      entity.setSourceUrl(sourceUrl);
      entity.setFetchedAt(now);

      // 详细 JCR 字段仅对最新年填充：这些字段在 Clarivate 原生 JCR 里本就是按年发布的年度指标，
      // LetPub 页面只提供最新年详细值，历史年暂留空，等后续接入 Clarivate 一级源后可回填。
      if (year == latestYear) {
        entity.setSubject(data.jcrSubject());
        entity.setCollection(data.jcrCollection());
        entity.setJifQuartile(data.jifQuartile());
        entity.setJifRank(data.jifRank());
        entity.setJifPercentile(toBigDecimal(data.jifPercentile()));
        entity.setJciSubject(data.jciSubject());
        entity.setJciCollection(data.jciCollection());
        entity.setJciQuartile(data.jciQuartile());
        entity.setJciRank(data.jciRank());
        entity.setJciPercentile(toBigDecimal(data.jciPercentile()));
        entity.setJciValue(toBigDecimal(data.jciValue()));
        entity.setWosOverallQuartile(data.wosOverallQuartile());
        entity.setSelfCitationRate(toBigDecimal(data.selfCitationRate()));
        entity.setResearchDirection(data.researchDirection());
      }

      ratings.add(entity);
    }

    return ratings;
  }

  /// 将 LetPub 所有 CAS 分区版本映射为多行 CAS 评级。
  ///
  /// LetPub 页面可能同时展示多个 CAS 版本（如新锐版/升级版/旧的升级版），
  /// 每个版本生成一行 `CasRatingEntity`，通过 `(venue_id, year, edition)`
  /// 唯一约束区分。
  ///
  /// @param data LetPub 原始数据
  /// @param venueId 目标 venue ID
  /// @param sourceUrl LetPub 详情页 URL（数据溯源）
  /// @return CAS 评级实体列表（可能为空）
  public List<CasRatingEntity> mapToCasRatings(
      LetPubVenueData data, Long venueId, String sourceUrl) {
    List<CasPartition> partitions = data.casPartitions();
    if (partitions == null || partitions.isEmpty()) {
      return List.of();
    }

    Instant now = Instant.now();
    List<CasRatingEntity> entities = new ArrayList<>();

    for (CasPartition partition : partitions) {
      String version = partition.version();
      String quartile = partition.majorQuartile();
      if (version == null || version.isBlank() || quartile == null || quartile.isBlank()) {
        continue;
      }

      short year = extractCasYear(version);
      if (year <= 0) {
        continue;
      }

      String edition = extractCasEdition(version);

      var entity = new CasRatingEntity();
      entity.setId(SnowflakeIdGenerator.getId());
      entity.setVenueId(venueId);
      entity.setYear(year);
      entity.setEdition(edition);
      entity.setMajorCategory(partition.majorCategory());
      entity.setMajorQuartile(quartile);
      entity.setMinorSubject(partition.minorSubject());
      entity.setMinorQuartile(partition.minorQuartile());
      entity.setIsTopJournal(partition.topJournal());
      entity.setIsReviewJournal(partition.reviewJournal());
      entity.setSourceUrl(sourceUrl);
      entity.setFetchedAt(now);

      entities.add(entity);
    }

    return entities;
  }

  /// 将 LetPub CAS 预警名单记录映射为 CasWarningEntity 列表。
  ///
  /// 与 `mapToCasRatings` 不同，预警名单是**独立的时间序列**，每条记录对应
  /// LetPub 页面预警名单行里的一段历史版本文本（由 Parser 解析而来）。
  ///
  /// @param data LetPub 原始数据
  /// @param venueId 目标 venue ID
  /// @param sourceUrl LetPub 详情页 URL（数据溯源）
  /// @return CAS 预警实体列表（可能为空）
  public List<CasWarningEntity> mapToCasWarnings(
      LetPubVenueData data, Long venueId, String sourceUrl) {
    List<CasWarningRecord> warnings = data.casWarnings();
    if (warnings == null || warnings.isEmpty()) {
      return List.of();
    }

    Instant now = Instant.now();
    List<CasWarningEntity> entities = new ArrayList<>();
    for (CasWarningRecord record : warnings) {
      CasWarningEntity entity = new CasWarningEntity();
      entity.setId(SnowflakeIdGenerator.getId());
      entity.setVenueId(venueId);
      entity.setPublishedYear((short) record.publishedYear());
      entity.setPublishedMonth(
          record.publishedMonth() != null ? record.publishedMonth().shortValue() : null);
      entity.setEditionLabel(record.editionLabel());
      entity.setInWarningList(record.inWarningList());
      entity.setWarningLevel(record.warningLevel());
      entity.setRawText(record.rawText());
      entity.setSourceUrl(sourceUrl);
      entity.setFetchedAt(now);
      entities.add(entity);
    }
    return entities;
  }

  /// 将 Double 桥接为 BigDecimal（null-safe）。用于 DTO（Double）→ Entity（BigDecimal）映射。
  private static BigDecimal toBigDecimal(Double value) {
    return value != null ? BigDecimal.valueOf(value) : null;
  }

  // ========== 年份/版本提取 ==========

  /// 从 "2024-2025" 提取后面的年份 2025。
  short extractLaterYear(String yearRange) {
    Matcher m = YEAR_RANGE_PATTERN.matcher(yearRange);
    if (m.find()) {
      return Short.parseShort(m.group(2));
    }
    return 0;
  }

  /// 从 "2025年3月升级版" 提取年份 2025。
  short extractCasYear(String casVersion) {
    Matcher m = CAS_VERSION_YEAR_PATTERN.matcher(casVersion);
    if (m.find()) {
      return Short.parseShort(m.group(1));
    }
    return 0;
  }

  /// 从 "2025年3月升级版" 提取版本名称 "升级版"。
  ///
  /// 提取规则：取"月"或"年"之后的尾部版本标识。
  /// 无法提取时默认返回 "升级版"（LetPub 最常见的版本）。
  ///
  /// @param casVersion CAS 原始版本字符串
  /// @return 版本名称（如"升级版"、"新锐版"、"基础版"）
  String extractCasEdition(String casVersion) {
    Matcher m = CAS_EDITION_PATTERN.matcher(casVersion);
    if (m.find()) {
      return m.group(1);
    }
    return "升级版";
  }
}
