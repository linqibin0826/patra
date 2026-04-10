package com.patra.catalog.infra.batch.venue.letpub;

import com.patra.catalog.domain.port.enrichment.LetPubVenueData;
import com.patra.catalog.infra.persistence.entity.CasRatingEntity;
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
/// - CAS 分区 → 单行 `CasRatingEntity`
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

      // 最新年份附加详情
      if (year == latestYear) {
        entity.setFiveYearIf(
            data.fiveYearImpactFactor() != null
                ? BigDecimal.valueOf(data.fiveYearImpactFactor())
                : null);
        entity.setSubject(data.jcrSubject());
        entity.setCollection(data.jcrCollection());
        entity.setJifQuartile(data.jifQuartile());
        entity.setJifRank(data.jifRank());
        entity.setJciQuartile(data.jciQuartile());
        entity.setJciRank(data.jciRank());
        entity.setResearchDirection(data.researchDirection());
      }

      ratings.add(entity);
    }

    return ratings;
  }

  /// 将 LetPub CAS 分区数据映射为单行 CAS 评级。
  ///
  /// @param data LetPub 原始数据
  /// @param venueId 目标 venue ID
  /// @param sourceUrl LetPub 详情页 URL（数据溯源）
  /// @return CAS 评级实体，无数据时返回 null
  public CasRatingEntity mapToCasRating(LetPubVenueData data, Long venueId, String sourceUrl) {
    String casVersion = data.casVersion();
    String quartile = data.casMajorQuartile();
    if (casVersion == null || casVersion.isBlank() || quartile == null || quartile.isBlank()) {
      return null;
    }

    short year = extractCasYear(casVersion);
    if (year <= 0) {
      return null;
    }

    String edition = extractCasEdition(casVersion);

    var entity = new CasRatingEntity();
    entity.setId(SnowflakeIdGenerator.getId());
    entity.setVenueId(venueId);
    entity.setYear(year);
    entity.setEdition(edition);
    entity.setMajorCategory(data.casMajorCategory());
    entity.setMajorQuartile(quartile);
    entity.setMinorSubject(data.casMinorSubject());
    entity.setMinorQuartile(data.casMinorQuartile());
    entity.setIsTopJournal(data.casTopJournal());
    entity.setIsReviewJournal(data.casReviewJournal());
    entity.setSourceUrl(sourceUrl);
    entity.setFetchedAt(Instant.now());

    return entity;
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
