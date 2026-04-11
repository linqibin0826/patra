package com.patra.catalog.domain.port.enrichment;

import java.util.List;
import java.util.Map;
import lombok.Builder;

/// LetPub 爬取原始数据 DTO。
///
/// 端口级中间数据结构，封装从 [LetPub](https://www.letpub.com.cn) 爬取的原始字段。
/// 由 {@link LetPubEnrichmentPort} 返回，由下游 Mapper 拆解为领域原生概念
/// （`cat_venue_jcr_rating` + `cat_venue_cas_rating` 评级行）。
///
/// **注意**：这不是领域值对象，而是数据源适配层的中间载体。
///
/// **数据分组**：
///
/// | 分组 | 字段 | 说明 |
/// |------|------|------|
/// | 基本信息 | letPubJournalId ~ researchArticlePercent | 期刊基础属性 |
/// | JCR 分区 | jcrSubject ~ jciRank | Journal Citation Reports 分区排名 |
/// | CAS 分区 | casPartitions | 中科院分区列表（支持多版本共存：新锐版/升级版/旧版） |
/// | 预警/审稿/费用 | warningListStatus ~ apcInfo | 投稿决策参考 |
/// | 收录情况 | indexedIn | 数据库收录列表 |
///
/// **字段来源边界**：
/// 出版国家、出版语言、出版周期、创刊年份等基础元数据由 PubMed NLM Serfile
/// 作为权威来源提供（见 `VenuePubmedImportHandler`），本 DTO 不携带这些字段以避免多源冲突。
///
/// **不变性**：Record + `@Builder` 自动保证不可变，
/// `impactFactorTrend` / `indexedIn` / `casPartitions` 通过紧凑构造器防御性拷贝。
///
/// @param letPubJournalId LetPub 内部期刊 ID
/// @param letPubName LetPub 显示的期刊名称
/// @param researchDirection 研究方向/学科领域
/// @param articlesPerYear 年发文量（可为 null）
/// @param goldOaPercent 金色 OA 百分比
/// @param researchArticlePercent 研究性文章占比
/// @param coverImageSourceUrl 封面图原始 URL（LetPub 托管于 Aliyun OSS CDN）
/// @param jcrSubject JCR 学科分类
/// @param jcrCollection JCR 大类
/// @param jifQuartile JIF（影响因子）分区
/// @param jifRank JIF 排名
/// @param jciQuartile JCI（期刊引文指标）分区
/// @param jciRank JCI 排名
/// @param casPartitions CAS 中科院分区列表（支持多版本，不可变）
/// @param warningListStatus 预警名单状态
/// @param reviewSpeedOfficial 官方审稿周期
/// @param reviewSpeedUser 用户反馈审稿周期
/// @param acceptanceRate 录用比例/难易度
/// @param apcInfo APC 费用信息
/// @param impactFactorTrend 近10年影响因子趋势（key="2024-2025"，value=IF值，不可变）
/// @param fiveYearImpactFactor 五年影响因子（可为 null）
/// @param indexedIn 数据库收录列表（不可变）
/// @author linqibin
/// @since 0.1.0
@Builder
public record LetPubVenueData(
    // 基本信息
    String letPubJournalId,
    String letPubName,
    String researchDirection,
    Integer articlesPerYear,
    String goldOaPercent,
    String researchArticlePercent,
    // 封面图（LetPub CDN 原始 URL）
    String coverImageSourceUrl,
    // JCR 分区
    String jcrSubject,
    String jcrCollection,
    String jifQuartile,
    String jifRank,
    String jciQuartile,
    String jciRank,
    // CAS 分区（多版本）
    List<CasPartition> casPartitions,
    // 预警 + 审稿 + 费用
    String warningListStatus,
    String reviewSpeedOfficial,
    String reviewSpeedUser,
    String acceptanceRate,
    String apcInfo,
    // 影响因子
    Map<String, Double> impactFactorTrend,
    Double fiveYearImpactFactor,
    // 收录
    List<String> indexedIn) {

  /// 紧凑构造器：对集合字段进行防御性拷贝。
  public LetPubVenueData {
    impactFactorTrend = impactFactorTrend != null ? Map.copyOf(impactFactorTrend) : Map.of();
    indexedIn = indexedIn != null ? List.copyOf(indexedIn) : List.of();
    casPartitions = casPartitions != null ? List.copyOf(casPartitions) : List.of();
  }

  /// CAS 中科院分区快照（单版本）。
  ///
  /// 对应 LetPub 页面上的一个分区表版本（如"2026年3月新锐版"、"2025年3月升级版"）。
  /// LetPub 页面通常同时展示多个版本，本记录捕获其中一个版本的分区信息。
  ///
  /// @param version 版本标识（如"2026年3月新锐版"）
  /// @param majorCategory 大类学科（如"综合性期刊"）
  /// @param majorQuartile 大类分区（"1区"-"4区"）
  /// @param minorSubject 小类学科（如"MULTIDISCIPLINARY SCIENCES"）
  /// @param minorQuartile 小类分区（"1区"-"4区"）
  /// @param topJournal 是否为 Top 期刊（可为 null）
  /// @param reviewJournal 是否为综述期刊（可为 null）
  @Builder
  public record CasPartition(
      String version,
      String majorCategory,
      String majorQuartile,
      String minorSubject,
      String minorQuartile,
      Boolean topJournal,
      Boolean reviewJournal) {}
}
