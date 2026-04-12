package com.patra.catalog.adapter.rest.venue.response;

import java.math.BigDecimal;
import lombok.Builder;

/// 期刊列表项响应。
///
/// @author linqibin
/// @since 0.1.0
@Builder
public record VenueItemResponse(
    Long id,
    String title,
    String countryCode,
    String imageObjectKey,
    Integer hIndex,
    String jifQuartile,
    String casMajorQuartile,
    Boolean casTopJournal,
    BigDecimal citeScore,
    String citeScoreQuartile,
    Boolean isOa,
    String researchDirection,
    BigDecimal impactFactor,
    String collection) {}
