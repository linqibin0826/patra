package com.patra.catalog.domain.model.read.venue;

import java.math.BigDecimal;
import lombok.Builder;

/// 期刊最新评级摘要（JCR/CAS/Scopus/预警），用于详情页头部展示核心指标。
///
/// 所有字段均可为 null：某数据源无数据时对应字段组整体为空。
///
/// @param jcrYear JCR 评级年份
/// @param impactFactor JIF 影响因子
/// @param jifQuartile JIF 分区（Q1-Q4）
/// @param jifRank JIF 排名（如 "2/136"）
/// @param jifPercentile JIF 学科百分位
/// @param wosOverallQuartile WOS 综合分区等级
/// @param collection JIF 收录集（SCIE/SSCI/AHCI）
/// @param selfCitationRate 自引率
/// @param researchDirection 研究方向/学科领域
/// @param jciValue JCI 数值
/// @param jciQuartile JCI 分区
/// @param casYear CAS 分区年份
/// @param casEdition CAS 版本名称（升级版/新锐版/基础版）
/// @param majorCategory 大类学科
/// @param majorQuartile 大类分区
/// @param minorSubject 小类学科
/// @param minorQuartile 小类分区
/// @param isTopJournal 是否为 Top 期刊
/// @param isReviewJournal 是否为综述期刊
/// @param scopusYear Scopus 评级年份
/// @param citeScore CiteScore
/// @param sjr SCImago Journal Rank
/// @param snip Source Normalized Impact per Paper
/// @param citeScoreQuartile CiteScore 分区（Q1-Q4）
/// @param citeScorePercentile CiteScore 学科百分位
/// @param inWarningList 是否在 CAS 预警名单中
/// @param warningLevel 预警级别（HIGH/MEDIUM/LOW）
@Builder
public record VenueLatestRating(
    // JCR
    Short jcrYear,
    BigDecimal impactFactor,
    String jifQuartile,
    String jifRank,
    BigDecimal jifPercentile,
    String wosOverallQuartile,
    String collection,
    BigDecimal selfCitationRate,
    String researchDirection,
    BigDecimal jciValue,
    String jciQuartile,
    // CAS
    Short casYear,
    String casEdition,
    String majorCategory,
    String majorQuartile,
    String minorSubject,
    String minorQuartile,
    Boolean isTopJournal,
    Boolean isReviewJournal,
    // Scopus
    Short scopusYear,
    BigDecimal citeScore,
    BigDecimal sjr,
    BigDecimal snip,
    String citeScoreQuartile,
    BigDecimal citeScorePercentile,
    // Warning
    Boolean inWarningList,
    String warningLevel) {}
