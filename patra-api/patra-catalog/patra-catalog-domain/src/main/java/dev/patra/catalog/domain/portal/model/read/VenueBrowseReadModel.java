package dev.patra.catalog.domain.portal.model.read;

import java.math.BigDecimal;
import lombok.Builder;

/// 期刊浏览列表读模型（CQRS 读端）。
///
/// @param id 期刊主键
/// @param name 期刊全称
/// @param abbr 缩写标题
/// @param coverObjectKey 封面图对象存储键，可空
/// @param impactFactor 最新年影响因子，可空
/// @param jcrQuartile JCR 综合分区（Q1-Q4），可空
/// @param jcrSubject JCR 学科分类，可空
/// @param casMajorCategory CAS 大类，可空
/// @param casMajorQuartile CAS 大类分区（Q1-Q4），可空
/// @param casIsTop 是否 CAS 顶刊，可空
/// @param countryCode 国家/地区码，可空
/// @param citedByCount 总被引量，可空
/// @param foundedYear 创刊年份，可空
/// @param isOpenAccess 是否开放获取，可空
/// @param isInDoaj 是否收录于 DOAJ，可空
/// @param issnL ISSN-L，可空
/// @param nlmId 主 NLM（身份锚，字符串保留前导零），可空
/// @author linqibin
/// @since 0.1.0
@Builder
public record VenueBrowseReadModel(
    Long id,
    String name,
    String abbr,
    String coverObjectKey,
    BigDecimal impactFactor,
    String jcrQuartile,
    String jcrSubject,
    String casMajorCategory,
    String casMajorQuartile,
    Boolean casIsTop,
    String countryCode,
    Integer citedByCount,
    Integer foundedYear,
    Boolean isOpenAccess,
    Boolean isInDoaj,
    String issnL,
    String nlmId) {}
