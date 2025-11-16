package com.patra.starter.provenance.epmc.model.request;

import com.patra.common.provenance.api.params.EpmcParamKeys;
import com.patra.common.provenance.api.values.epmc.Format;
import com.patra.starter.provenance.common.gateway.ApiRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Europe PMC 搜索API请求参数
 *
 * <p>Europe PMC支持Lucene风格的查询语法，提供丰富的过滤和排序选项。
 *
 * <p>参数说明：
 *
 * @param query Lucene风格的搜索查询字符串（必需）
 * @param format 响应格式（json或xml），默认json
 * @param pageSize 每页返回的记录数
 * @param cursorMark 游标令牌，用于深度分页
 * @param sort EPMC支持的排序策略字符串
 * @param resultType 结果投影类型（lite、core等）
 * @param synonym 是否用同义词扩展查询
 * @param fromSearchPost 标识是否基于POST执行
 * @param searchType 可选的搜索上下文修饰符
 * @author linqibin
 * @since 0.1.0
 */
public record SearchRequest(
    String query,
    String format,
    Integer pageSize,
    String cursorMark,
    String sort,
    String resultType,
    Boolean synonym,
    Boolean fromSearchPost,
    String searchType)
    implements ApiRequest {

  /**
   * 使用JSON输出和默认分页创建请求
   *
   * @param query Lucene风格的搜索查询
   */
  public SearchRequest(String query) {
    this(query, Format.JSON.value(), null, null, null, null, null, null, null);
  }

  public SearchRequest {
    if (query == null || query.isBlank()) {
      throw new IllegalArgumentException("query 参数不能为null或空白");
    }
    if (format == null || format.isBlank()) {
      format = Format.JSON.value();
    }
  }

  /**
   * 组装Europe PMC端点理解的查询参数映射
   *
   * @return 准备提交到网关的参数映射
   */
  @Override
  public Map<String, String> toQueryParams() {
    Map<String, String> params = new LinkedHashMap<>();
    params.put(EpmcParamKeys.QUERY, query);
    params.put(EpmcParamKeys.FORMAT, format);
    if (pageSize != null) {
      params.put(EpmcParamKeys.PAGE_SIZE, pageSize.toString());
    }
    if (cursorMark != null && !cursorMark.isBlank()) {
      params.put(EpmcParamKeys.CURSOR_MARK, cursorMark);
    }
    if (sort != null && !sort.isBlank()) {
      params.put(EpmcParamKeys.SORT, sort);
    }
    if (resultType != null && !resultType.isBlank()) {
      params.put(EpmcParamKeys.RESULT_TYPE, resultType);
    }
    if (synonym != null) {
      params.put(EpmcParamKeys.SYNONYM, synonym.toString());
    }
    if (fromSearchPost != null) {
      params.put(EpmcParamKeys.FROM_SEARCH_POST, fromSearchPost.toString());
    }
    if (searchType != null && !searchType.isBlank()) {
      params.put(EpmcParamKeys.SEARCH_TYPE, searchType);
    }
    return params;
  }
}
