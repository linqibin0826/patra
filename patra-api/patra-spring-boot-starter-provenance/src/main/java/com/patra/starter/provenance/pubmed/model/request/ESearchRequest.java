package com.patra.starter.provenance.pubmed.model.request;

import com.patra.starter.provenance.common.gateway.ApiRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PubMed ESearch API 请求参数
 *
 * <p>与官方E-utilities文档对齐的参数定义。ESearch用于搜索PubMed数据库并返回匹配的PMID列表。
 *
 * <p>字段说明：
 *
 * @param db 目标数据库标识符（如"pubmed"）
 * @param term 布尔查询字符串（当使用日期过滤器时可选）
 * @param retstart 分页的从零开始的偏移量
 * @param retmax 每次调用返回的最大记录数（最多10000）
 * @param retmode 响应格式（json或xml）
 * @param rettype 响应类型（uilist、count等）
 * @param sort PubMed应用的排序策略
 * @param datetype 用于过滤评估的出版日期字段
 * @param mindate 日期约束下限
 * @param maxdate 日期约束上限
 * @param field 字段特定的搜索限制
 * @param reldate 以天为单位表示的相对日期过滤器
 * @param usehistory 是否使用PubMed历史服务器
 * @param webenv 历史会话WebEnv令牌
 * @param queryKey 指向存储查询的数字键
 * @param apiKey 授予提升速率限制的API密钥
 * @param tool 在NCBI注册的客户端标识符
 * @param email NCBI通知的联系邮箱
 * @author linqibin
 * @since 0.1.0
 */
public record ESearchRequest(
    // 必需参数
    String db, // 数据库名称（如"pubmed"）

    // 搜索词（当提供日期过滤器mindate/maxdate/datetype时可选）
    String term,

    // Optional parameters - Basic control
    Integer retstart, // Start position (default 0)
    Integer retmax, // Return count (default 20, max 10000)
    String retmode, // Return mode (json/xml, default json)
    String rettype, // Return type (uilist/count)

    // Optional parameters - Sorting and filtering
    String sort, // Sort method (relevance/pub_date/Author/JournalName)
    String datetype, // Date type (pdat/edat/mdat)
    String mindate, // Minimum date (YYYY/MM/DD or YYYY)
    String maxdate, // Maximum date (YYYY/MM/DD or YYYY)
    String field, // Search field (limit search scope)
    String reldate, // Relative date (days)

    // Optional parameters - History and session
    String usehistory, // Use history (y/n)
    String webenv, // Web environment string
    String queryKey, // Query key

    // Optional parameters - Authentication and identification (IMPORTANT)
    String apiKey, // API Key (increase rate limit: 3 req/sec → 10 req/sec)
    String tool, // Tool name (identify application, e.g., "papertrace")
    String email // Contact email (NCBI can contact developer)
    ) implements ApiRequest {

  /**
   * Create a request that targets the PubMed database using JSON output.
   *
   * @param db database identifier, typically "pubmed"
   * @param term Boolean query string that drives the search
   */
  public ESearchRequest(String db, String term) {
    this(
        db, term, null, null, "json", null, null, null, null, null, null, null, null, null, null,
        null, null, null);
  }

  // Compact constructor: validate required parameters
  public ESearchRequest {
    if (db == null || db.isBlank()) {
      throw new IllegalArgumentException("db cannot be null or blank");
    }
    // Note: term is optional when date filters (mindate/maxdate/datetype) are provided
    // Default to JSON format
    if (retmode == null || retmode.isBlank()) {
      retmode = "json";
    }
  }

  /**
   * Compose the outbound query parameter map understood by the ESearch endpoint.
   *
   * @return parameter map ready for gateway submission
   */
  @Override
  public Map<String, String> toQueryParams() {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("db", db);
    // term is optional - only add when present (date filters can be used instead)
    if (term != null && !term.isBlank()) {
      params.put("term", term);
    }

    // Basic control
    if (retstart != null) params.put("retstart", retstart.toString());
    if (retmax != null) params.put("retmax", retmax.toString());
    params.put("retmode", retmode != null ? retmode : "json");
    if (rettype != null) params.put("rettype", rettype);

    // Sorting and filtering
    if (sort != null) params.put("sort", sort);
    if (datetype != null) params.put("datetype", datetype);
    if (mindate != null) params.put("mindate", mindate);
    if (maxdate != null) params.put("maxdate", maxdate);
    if (field != null) params.put("field", field);
    if (reldate != null) params.put("reldate", reldate);

    // History and session
    if (usehistory != null) params.put("usehistory", usehistory);
    if (webenv != null) params.put("WebEnv", webenv);
    if (queryKey != null) params.put("query_key", queryKey);

    // Authentication and identification
    if (apiKey != null) params.put("api_key", apiKey);
    if (tool != null) params.put("tool", tool);
    if (email != null) params.put("email", email);

    return params;
  }
}
