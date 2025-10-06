package com.patra.starter.provenance.epmc.model.response;

import java.util.List;

/**
 * EPMC search API response
 *
 * @author linqibin
 * @since 0.1.0
 */
public record SearchResponse(
    Integer hitCount,       // Total result count
    String nextCursorMark,  // Next page cursor
    List<Result> resultList // Result list
) {
}
