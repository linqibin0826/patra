package dev.patra.catalog.infra.portal.persistence;

import java.math.BigDecimal;

/// [VenueBrowseReadAdapter] 的 SQL 查询行，组件名与列别名一一对应。
///
/// @param nlmId 主 NLM（身份锚，字符串保留前导零）。
/// @author linqibin
/// @since 0.1.0
record VenueBrowseRow(
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
