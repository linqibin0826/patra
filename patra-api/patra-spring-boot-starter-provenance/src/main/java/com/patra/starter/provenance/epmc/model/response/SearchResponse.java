package com.patra.starter.provenance.epmc.model.response;

import com.patra.starter.provenance.common.support.JsonHelpers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import tools.jackson.databind.JsonNode;

/// Europe PMC 搜索响应结构化表示
///
/// 暴露精选字段，同时提供原始JSON访问以支持下游处理。
///
/// 设计特点：
///
/// - 类型安全：将JSON响应映射为强类型Java对象
///   - 防御性解析：容忍缺失字段和格式变化
///   - 原始数据保留：保留完整JSON以支持高级用例
///
/// @author linqibin
/// @since 0.1.0
public final class SearchResponse {

  private final String version;
  private final long hitCount;
  private final String nextCursorMark;
  private final String nextPageUrl;
  private final Request request;
  private final List<Result> results;
  private final JsonNode raw;

  private SearchResponse(
      String version,
      long hitCount,
      String nextCursorMark,
      String nextPageUrl,
      Request request,
      List<Result> results,
      JsonNode raw) {
    this.version = version;
    this.hitCount = hitCount;
    this.nextCursorMark = nextCursorMark;
    this.nextPageUrl = nextPageUrl;
    this.request = request;
    this.results = results;
    this.raw = raw;
  }

  /// 将Europe PMC响应树解析为强类型表示
  ///
  /// @param root 响应根节点
  /// @return 结构化响应视图
  public static SearchResponse from(JsonNode root) {
    Objects.requireNonNull(root, "root cannot be null");
    String version = JsonHelpers.textValue(root.path("version"));
    long hitCount = root.path("hitCount").asLong(0);
    String nextCursorMark = JsonHelpers.textValue(root.path("nextCursorMark"));
    String nextPageUrl = JsonHelpers.textValue(root.path("nextPageUrl"));
    Request request = Request.from(root.path("request"));

    List<Result> results = new ArrayList<>();
    for (JsonNode resultNode : JsonHelpers.toNodeList(root.path("resultList").path("result"))) {
      results.add(Result.from(resultNode));
    }
    return new SearchResponse(
        version,
        hitCount,
        nextCursorMark,
        nextPageUrl,
        request,
        Collections.unmodifiableList(results),
        root.deepCopy());
  }

  /// 为无操作场景创建空响应占位符
  ///
  /// @return 空响应实例
  public static SearchResponse empty() {
    return new SearchResponse(null, 0, null, null, Request.empty(), Collections.emptyList(), null);
  }

  /// 获取Europe PMC报告的API版本
  public String version() {
    return version;
  }

  /// 获取查询的总命中数
  public long hitCount() {
    return hitCount;
  }

  /// 获取用于继续分页的游标标记
  public String nextCursorMark() {
    return nextCursorMark;
  }

  /// 获取下一页链接URL（如果提供）
  public String nextPageUrl() {
    return nextPageUrl;
  }

  /// 获取Europe PMC返回的结构化请求回显
  public Request request() {
    return request;
  }

  /// 获取搜索结果的不可变列表
  public List<Result> results() {
    return results;
  }

  /// 获取原始JSON载荷供高级消费者使用
  public JsonNode raw() {
    return raw;
  }

  /// Europe PMC返回的请求回显参数
  ///
  /// 字段说明：
  ///
  /// @param queryString 解析后的查询字符串
  /// @param resultType 请求的结果投影
  /// @param cursorMark 深度分页的游标令牌
  /// @param pageSize API回显的页面大小
  /// @param sort 应用于结果的排序
  /// @param synonym 是否启用同义词扩展
  /// @author linqibin
  /// @since 0.1.0
  public record Request(
      String queryString,
      String resultType,
      String cursorMark,
      Integer pageSize,
      String sort,
      Boolean synonym) {
    private static Request empty() {
      return new Request(null, null, null, null, null, null);
    }

    private static Request from(JsonNode node) {
      if (node == null || node.isMissingNode() || node.isNull()) {
        return empty();
      }
      Integer pageSize = node.hasNonNull("pageSize") ? node.path("pageSize").asInt() : null;
      Boolean synonym = node.hasNonNull("synonym") ? node.path("synonym").asBoolean() : null;
      return new Request(
          JsonHelpers.textValue(node.path("queryString")),
          JsonHelpers.textValue(node.path("resultType")),
          JsonHelpers.textValue(node.path("cursorMark")),
          pageSize,
          JsonHelpers.textValue(node.path("sort")),
          synonym);
    }
  }

  /// 单个搜索结果摘要
  ///
  /// 字段说明：
  ///
  /// @param id Europe PMC标识符
  /// @param source 来源仓库（如MED）
  /// @param pmid PubMed标识符（如果可用）
  /// @param pmcid PubMed Central标识符（如果可用）
  /// @param doi 数字对象标识符
  /// @param title 文章标题
  /// @param authorString 连接的作者列表
  /// @param journalTitle 期刊名称
  /// @param pubYear 发表年份
  /// @param journalIssn ISSN代码
  /// @param pageInfo 页面信息字符串
  /// @param pubType 出版物类型
  /// @param abstractText 摘要片段
  /// @param citedByCount 引用计数
  /// @param raw 记录的原始JSON节点
  /// @author linqibin
  /// @since 0.1.0
  public record Result(
      String id,
      String source,
      String pmid,
      String pmcid,
      String doi,
      String title,
      String authorString,
      String journalTitle,
      String pubYear,
      String journalIssn,
      String pageInfo,
      String pubType,
      String abstractText,
      Integer citedByCount,
      JsonNode raw) {
    private static Result from(JsonNode node) {
      if (node == null || node.isMissingNode() || node.isNull()) {
        return new Result(
            null, null, null, null, null, null, null, null, null, null, null, null, null, null,
            null);
      }
      Integer citedBy = node.hasNonNull("citedByCount") ? node.path("citedByCount").asInt() : null;
      return new Result(
          JsonHelpers.textValue(node.path("id")),
          JsonHelpers.textValue(node.path("source")),
          JsonHelpers.textValue(node.path("pmid")),
          JsonHelpers.textValue(node.path("pmcid")),
          JsonHelpers.textValue(node.path("doi")),
          JsonHelpers.textValue(node.path("title")),
          JsonHelpers.textValue(node.path("authorString")),
          JsonHelpers.textValue(node.path("journalTitle")),
          JsonHelpers.textValue(node.path("pubYear")),
          JsonHelpers.textValue(node.path("journalIssn")),
          JsonHelpers.textValue(node.path("pageInfo")),
          JsonHelpers.textValue(node.path("pubType")),
          JsonHelpers.textValue(node.path("abstractText")),
          citedBy,
          node.deepCopy());
    }
  }
}
