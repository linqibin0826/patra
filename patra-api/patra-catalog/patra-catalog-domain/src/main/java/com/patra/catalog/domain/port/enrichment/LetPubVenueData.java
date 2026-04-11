package com.patra.catalog.domain.port.enrichment;

import com.patra.catalog.domain.model.enums.CasWarningLevel;
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
/// | JCR/WOS 指标 | wosOverallQuartile ~ selfCitationRate | WOS/Clarivate 评级体系 |
/// | CAS 分区 | casPartitions | 中科院分区列表（支持多版本共存：新锐版/升级版/旧版） |
/// | CAS 预警 | casWarnings | 中科院期刊预警名单时间序列（每版本一条） |
/// | 审稿/费用 | reviewSpeedOfficial ~ apcInfo | 投稿决策参考 |
/// | 收录情况 | indexedIn | 数据库收录列表 |
///
/// **数据源主权边界**（避免多源冲突）：
///
/// LetPub 页面展示的部分字段在 Patra 中已有权威数据源，LetPub 作为二级来源**不应抓取**：
///
/// - `h-index` → OpenAlex（`cat_venue.citation_metrics.hIndex`）
/// - `CiteScore` / `SJR` / `SNIP` → Elsevier Scopus API（`cat_venue_scopus_rating`）
/// - `出版国家/语言/周期/创刊年份` → PubMed NLM Serfile（`cat_venue` 快速访问列）
///
/// LetPub 作为**便捷的二级来源**覆盖：**JCR/WOS 年度指标**（Clarivate 是一级源）、
/// **CAS 中科院分区与预警**（中科院是一级源）、以及 **LetPub 独有的审稿/录用/APC 等
/// 投稿决策信息**。未来接入 Clarivate/中科院一级源时，JCR/CAS 类字段可继续由一级源补全。
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
/// @param wosOverallQuartile WOS 综合分区等级（如 `1区`，综合 JIF + JCI 评定）
/// @param jcrSubject JIF 学科分类（与 JIF 子表的 subject 对应）
/// @param jcrCollection JIF 收录子集（SCIE/SSCI/AHCI）
/// @param jifQuartile JIF（影响因子）分区
/// @param jifRank JIF 排名（如 `2/136`）
/// @param jifPercentile JIF 学科百分位（0.00-100.00，如 `99.0`）
/// @param jciSubject JCI 学科分类（多数情况下同 jcrSubject）
/// @param jciCollection JCI 收录子集（多数情况下同 jcrCollection）
/// @param jciQuartile JCI（期刊引文指标）分区
/// @param jciRank JCI 排名
/// @param jciPercentile JCI 学科百分位（0.00-100.00，如 `98.9`）
/// @param jciValue JCI 数值（Journal Citation Indicator 本身的数值，如 `11.14`）
/// @param selfCitationRate 自引率（0.00-100.00，如 `1.6`；Clarivate 年度指标，LetPub 仅提供最新年值）
/// @param casPartitions CAS 中科院分区列表（支持多版本，不可变）
/// @param casWarnings CAS 中科院期刊预警名单时间序列（按版本，不可变）
/// @param reviewSpeedOfficial 官方审稿周期
/// @param reviewSpeedUser 用户反馈审稿周期
/// @param acceptanceRate 录用比例/难易度
/// @param apcInfo APC 费用信息
/// @param impactFactorTrend 近10年影响因子趋势（key="2024-2025"，value=IF值，不可变）
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
    // JCR/WOS 年度指标：Clarivate 每版 JCR 都按年发布，LetPub 作为二级来源仅提供最新年详细值
    String wosOverallQuartile,
    String jcrSubject,
    String jcrCollection,
    String jifQuartile,
    String jifRank,
    Double jifPercentile,
    String jciSubject,
    String jciCollection,
    String jciQuartile,
    String jciRank,
    Double jciPercentile,
    Double jciValue,
    Double selfCitationRate,
    // CAS 分区（多版本）
    List<CasPartition> casPartitions,
    // CAS 预警名单（时间序列）
    List<CasWarningRecord> casWarnings,
    // 审稿 + 费用
    String reviewSpeedOfficial,
    String reviewSpeedUser,
    String acceptanceRate,
    String apcInfo,
    // 影响因子（近 10 年趋势）
    Map<String, Double> impactFactorTrend,
    // 收录
    List<String> indexedIn) {

  /// 紧凑构造器：对集合字段进行防御性拷贝。
  public LetPubVenueData {
    impactFactorTrend = impactFactorTrend != null ? Map.copyOf(impactFactorTrend) : Map.of();
    indexedIn = indexedIn != null ? List.copyOf(indexedIn) : List.of();
    casPartitions = casPartitions != null ? List.copyOf(casPartitions) : List.of();
    casWarnings = casWarnings != null ? List.copyOf(casWarnings) : List.of();
  }

  /// CAS 中科院期刊预警名单记录（单版本）。
  ///
  /// 对应 LetPub 页面 "期刊分区表预警名单" 行内每一段历史版本文本。
  /// 每条记录代表一个具体版本（如"2025年03月发布的2025版"）的预警状态。
  ///
  /// **设计说明**：
  ///
  /// - 预警名单是**独立于 CAS 分区表的时间序列**，发布节奏和版本命名均不同步
  /// - 预警版本标签（如 `新锐学术版`/`2025版`/`2024版`）和分区版本标签
  ///   （如 `新锐版`/`升级版`/`基础版`）命名风格不同，不应强行对齐
  /// - `warningLevel` 仅在 `inWarningList=true` 时可能有值（HIGH/MEDIUM/LOW）
  ///
  /// @param publishedYear 预警名单发布年份
  /// @param publishedMonth 预警名单发布月份（可空）
  /// @param editionLabel 原始版本标签（如 `2025版`、`新锐学术版`）
  /// @param inWarningList 是否在预警名单中
  /// @param warningLevel 预警级别（`HIGH`/`MEDIUM`/`LOW`，可空）
  /// @param rawText 原始描述文本（保留 LetPub 页面原句以便追溯）
  @Builder
  public record CasWarningRecord(
      int publishedYear,
      Integer publishedMonth,
      String editionLabel,
      boolean inWarningList,
      CasWarningLevel warningLevel,
      String rawText) {}

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
