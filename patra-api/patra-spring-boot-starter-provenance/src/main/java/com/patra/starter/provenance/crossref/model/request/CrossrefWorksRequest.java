package com.patra.starter.provenance.crossref.model.request;

import com.patra.common.provenance.api.params.CrossrefParamKeys;
import com.patra.starter.provenance.common.gateway.ApiRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Crossref Works API 请求
 *
 * <p>Crossref是学术出版物引用数据库，提供DOI解析和文献检索服务。 支持布尔查询（query参数）和过滤器组合（filter参数）。
 *
 * <p>主要特性：
 *
 * <ul>
 *   <li>布尔查询：通过query参数支持复杂的搜索表达式
 *   <li>过滤器：通过filter参数按时间、类型等维度筛选
 *   <li>分页：支持offset/cursor两种分页模式
 *   <li>礼貌池：提供mailto参数可获得更高的请求限额
 * </ul>
 *
 * @param query 布尔查询表达式（与filter至少提供一个）
 * @param filter 过滤器表达式，格式：filter=key:value
 * @param rows 每页返回记录数
 * @param offset 偏移量，用于传统分页
 * @param sort 排序字段
 * @param order 排序方向（asc/desc）
 * @param cursor 游标，用于深度分页（推荐）
 * @param select 选择返回字段，减少响应体积
 * @param mailto 联系邮箱，加入礼貌池获得更高限额
 * @author linqibin
 * @since 0.1.0
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
      throw new IllegalArgumentException("Crossref 请求必须提供 'query' 或 'filter' 中的至少一个");
    }
    if (rows != null && rows <= 0) {
      throw new IllegalArgumentException("Crossref 'rows' 参数必须为正数");
    }
    if (offset != null && offset < 0) {
      throw new IllegalArgumentException("Crossref 'offset' 参数必须为零或正数");
    }
  }

  @Override
  public Map<String, String> toQueryParams() {
    Map<String, String> params = new LinkedHashMap<>();
    if (query != null && !query.isBlank()) {
      params.put(CrossrefParamKeys.QUERY, query);
    }
    if (filter != null && !filter.isBlank()) {
      params.put(CrossrefParamKeys.FILTER, filter);
    }
    if (rows != null) {
      params.put(CrossrefParamKeys.ROWS, rows.toString());
    }
    if (offset != null) {
      params.put(CrossrefParamKeys.OFFSET, offset.toString());
    }
    if (sort != null && !sort.isBlank()) {
      params.put(CrossrefParamKeys.SORT, sort);
    }
    if (order != null && !order.isBlank()) {
      params.put(CrossrefParamKeys.ORDER, order);
    }
    if (cursor != null && !cursor.isBlank()) {
      params.put(CrossrefParamKeys.CURSOR, cursor);
    }
    if (select != null && !select.isBlank()) {
      params.put(CrossrefParamKeys.SELECT, select);
    }
    if (mailto != null && !mailto.isBlank()) {
      params.put(CrossrefParamKeys.MAILTO, mailto);
    }
    return params;
  }
}
