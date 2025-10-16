package com.patra.starter.provenance.crossref.model.request;

import com.patra.starter.provenance.common.gateway.ApiRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Crossref works endpoint request. Supports boolean query (`query`) and filter composition via the
 * provider's `filter` parameter.
 */
public record CrossrefWorksRequest(
    String query,
    String filter,
    Integer rows,
    Integer offset,
    String sort,
    String order,
    String cursor,
    String select,
    String mailto)
    implements ApiRequest {

  public CrossrefWorksRequest {
    if ((query == null || query.isBlank()) && (filter == null || filter.isBlank())) {
      throw new IllegalArgumentException(
          "Crossref request requires at least one of 'query' or 'filter'");
    }
    if (rows != null && rows <= 0) {
      throw new IllegalArgumentException("Crossref 'rows' must be positive when provided");
    }
    if (offset != null && offset < 0) {
      throw new IllegalArgumentException("Crossref 'offset' must be zero or positive");
    }
  }

  @Override
  public Map<String, String> toQueryParams() {
    Map<String, String> params = new LinkedHashMap<>();
    if (query != null && !query.isBlank()) {
      params.put("query", query);
    }
    if (filter != null && !filter.isBlank()) {
      params.put("filter", filter);
    }
    if (rows != null) {
      params.put("rows", rows.toString());
    }
    if (offset != null) {
      params.put("offset", offset.toString());
    }
    if (sort != null && !sort.isBlank()) {
      params.put("sort", sort);
    }
    if (order != null && !order.isBlank()) {
      params.put("order", order);
    }
    if (cursor != null && !cursor.isBlank()) {
      params.put("cursor", cursor);
    }
    if (select != null && !select.isBlank()) {
      params.put("select", select);
    }
    if (mailto != null && !mailto.isBlank()) {
      params.put("mailto", mailto);
    }
    return params;
  }
}
