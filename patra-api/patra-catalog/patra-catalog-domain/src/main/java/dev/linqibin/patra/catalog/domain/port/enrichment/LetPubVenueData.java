package dev.linqibin.patra.catalog.domain.port.enrichment;

import dev.linqibin.patra.catalog.domain.model.enums.CasWarningLevel;
import java.util.List;
import java.util.Map;
import lombok.Builder;

/// LetPub 爬取原始数据 DTO。
///
/// 端口级中间数据结构，封装从 [LetPub](https://www.letpub.com.cn) 爬取的原始字段。
/// 由 {@link LetPubEnrichmentPort} 返回，由下游 Mapper 拆解为领域原生概念
/// （`cat_venue_jcr_rating` + `cat_venue_cas_rating` + `cat_venue_cas_warning`）。
///
/// **注意**：这不是领域值对象，而是数据源适配层的中间载体。
///
/// **结构**：按语义分为 4 个子 record，避免单一 record 27+ 参数的 god DTO 反模式：
///
/// | 子 record | 职责 |
/// |-----------|------|
/// | {@link BasicInfo} | 期刊标识/名称/封面 + 基础元数据 + 收录情况 |
/// | {@link JcrMetrics} | JCR/WOS 年度指标（Clarivate 每版 JCR 发布）+ IF 趋势 |
/// | {@link CasData} | CAS 中科院分区 + 预警名单（两条独立时间序列） |
/// | {@link SubmissionInfo} | LetPub 独有的审稿/录用/APC 投稿决策信息 |
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
/// **不变性**：Record + 紧凑构造器 null-safe 默认值 + 集合字段防御性拷贝。
///
/// @param basicInfo 基本信息（不可为 null，空实例通过 {@link BasicInfo#empty()} 提供）
/// @param jcrMetrics JCR/WOS 年度指标（不可为 null，空实例通过 {@link JcrMetrics#empty()} 提供）
/// @param casData CAS 分区 + 预警（不可为 null，空实例通过 {@link CasData#empty()} 提供）
/// @param submissionInfo 投稿决策信息（不可为 null，空实例通过 {@link SubmissionInfo#empty()} 提供）
/// @author linqibin
/// @since 0.1.0
public record LetPubVenueData(
    BasicInfo basicInfo, JcrMetrics jcrMetrics, CasData casData, SubmissionInfo submissionInfo) {

  /// 紧凑构造器：对 4 个子 record 做 null 归一化，避免下游 NPE。
  public LetPubVenueData {
    basicInfo = basicInfo != null ? basicInfo : BasicInfo.empty();
    jcrMetrics = jcrMetrics != null ? jcrMetrics : JcrMetrics.empty();
    casData = casData != null ? casData : CasData.empty();
    submissionInfo = submissionInfo != null ? submissionInfo : SubmissionInfo.empty();
  }

  /// 创建 LetPubVenueData 实例。
  public static LetPubVenueData of(
      BasicInfo basicInfo, JcrMetrics jcrMetrics, CasData casData, SubmissionInfo submissionInfo) {
    return new LetPubVenueData(basicInfo, jcrMetrics, casData, submissionInfo);
  }

  /// 创建全空实例（所有子 record 皆为空）。
  public static LetPubVenueData empty() {
    return new LetPubVenueData(
        BasicInfo.empty(), JcrMetrics.empty(), CasData.empty(), SubmissionInfo.empty());
  }

  /// 基本信息子 record。
  ///
  /// 包含期刊的身份标识、显示信息、封面资源，以及数据库收录情况等"非指标类"的元数据。
  /// 研究方向/年发文量/OA 占比等也属于此组，因为它们不随 JCR 年度版本变化。
  ///
  /// @param letPubJournalId LetPub 内部期刊 ID
  /// @param letPubName LetPub 显示的期刊名称
  /// @param coverImageSourceUrl 封面图原始 URL（LetPub CDN）
  /// @param researchDirection 研究方向/学科领域
  /// @param articlesPerYear 年发文量（可为 null）
  /// @param goldOaPercent 金色 OA 百分比
  /// @param researchArticlePercent 研究性文章占比
  /// @param indexedIn 数据库收录列表（不可变，防御性拷贝）
  @Builder
  public record BasicInfo(
      String letPubJournalId,
      String letPubName,
      String coverImageSourceUrl,
      String researchDirection,
      Integer articlesPerYear,
      String goldOaPercent,
      String researchArticlePercent,
      List<String> indexedIn) {

    /// 紧凑构造器：对 indexedIn 做防御性拷贝。
    public BasicInfo {
      indexedIn = indexedIn != null ? List.copyOf(indexedIn) : List.of();
    }

    /// 创建空 BasicInfo（所有字段为 null / 空列表）。
    public static BasicInfo empty() {
      return builder().build();
    }
  }

  /// JCR/WOS 年度指标子 record。
  ///
  /// Clarivate JCR 按年发布的年度指标集合（每版 JCR 每本期刊都有新值）。当前 LetPub 作为
  /// 二级来源仅提供最新年的详细分区/排名/百分位，以及近 10 年 IF 趋势——历史年的分区等字段
  /// 待未来接入 Clarivate 一级源回填。
  ///
  /// @param wosOverallQuartile WOS 综合分区等级（如 `1区`，LetPub 综合 JIF + JCI 评定）
  /// @param jcrSubject JIF 学科分类（如 `MULTIDISCIPLINARY SCIENCES`）
  /// @param jcrCollection JIF 收录子集（SCIE/SSCI/AHCI）
  /// @param jifQuartile JIF（影响因子）分区（Q1-Q4）
  /// @param jifRank JIF 排名（如 `2/136`）
  /// @param jifPercentile JIF 学科百分位（0.00-100.00）
  /// @param jciSubject JCI 学科分类（多数情况下同 jcrSubject）
  /// @param jciCollection JCI 收录子集（多数情况下同 jcrCollection）
  /// @param jciQuartile JCI（期刊引文指标）分区（Q1-Q4）
  /// @param jciRank JCI 排名
  /// @param jciPercentile JCI 学科百分位（0.00-100.00）
  /// @param jciValue JCI 数值（Journal Citation Indicator 本身的数值，如 `11.14`）
  /// @param selfCitationRate 自引率（0.00-100.00）
  /// @param impactFactorTrend 近 10 年影响因子趋势（key=`2024-2025`，value=IF 值，不可变）
  @Builder
  public record JcrMetrics(
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
      Map<String, Double> impactFactorTrend) {

    /// 紧凑构造器：对 impactFactorTrend 做防御性拷贝。
    public JcrMetrics {
      impactFactorTrend = impactFactorTrend != null ? Map.copyOf(impactFactorTrend) : Map.of();
    }

    /// 创建空 JcrMetrics（所有字段为 null / 空 Map）。
    public static JcrMetrics empty() {
      return builder().build();
    }
  }

  /// CAS 中科院数据子 record。
  ///
  /// 包含 CAS 分区（多版本）和预警名单（时间序列）两条**独立的时间序列**——发布节奏不同、
  /// 版本命名风格不同、历史覆盖不同，但语义上都来自中科院 CAS 体系，故在同一 sub record 里
  /// 以两个独立 List 承载。
  ///
  /// @param partitions CAS 分区列表（支持多版本共存：新锐版/升级版/基础版等）
  /// @param warnings CAS 预警名单时间序列（按版本，独立于 partitions 的发布周期）
  public record CasData(List<CasPartition> partitions, List<CasWarningRecord> warnings) {

    /// 紧凑构造器：对两个 List 做防御性拷贝。
    public CasData {
      partitions = partitions != null ? List.copyOf(partitions) : List.of();
      warnings = warnings != null ? List.copyOf(warnings) : List.of();
    }

    /// 创建 CasData 实例。
    public static CasData of(List<CasPartition> partitions, List<CasWarningRecord> warnings) {
      return new CasData(partitions, warnings);
    }

    /// 创建空 CasData（两个 List 均为空）。
    public static CasData empty() {
      return new CasData(List.of(), List.of());
    }
  }

  /// LetPub 独有的投稿决策信息子 record。
  ///
  /// 包含官方/网友两版审稿速度、录用率/难度、APC 费用信息等——这些字段是 LetPub 作为"投稿
  /// 参考工具"的独有数据，在一级学术数据源（Clarivate/Scopus/中科院）里**通常不提供**。
  ///
  /// @param reviewSpeedOfficial 官方审稿周期（LetPub 从期刊官网抓取）
  /// @param reviewSpeedUser 用户反馈审稿周期（LetPub 用户投稿后反馈汇总）
  /// @param acceptanceRate 录用比例/难易度
  /// @param apcInfo APC（Article Processing Charge）费用信息
  public record SubmissionInfo(
      String reviewSpeedOfficial, String reviewSpeedUser, String acceptanceRate, String apcInfo) {

    /// 创建 SubmissionInfo 实例。
    public static SubmissionInfo of(
        String reviewSpeedOfficial, String reviewSpeedUser, String acceptanceRate, String apcInfo) {
      return new SubmissionInfo(reviewSpeedOfficial, reviewSpeedUser, acceptanceRate, apcInfo);
    }

    /// 创建空 SubmissionInfo（所有字段为 null）。
    public static SubmissionInfo empty() {
      return new SubmissionInfo(null, null, null, null);
    }
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
