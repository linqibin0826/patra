package com.patra.starter.provenance.pubmed.model.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import tools.jackson.databind.JsonNode;

/// PubMed ESearch 响应的强类型视图。
///
/// ESearch 用于搜索 PubMed 数据库并返回匹配的文章标识符列表。响应包含搜索结果的元数据、 ID 列表、查询翻译信息以及可选的历史服务器令牌。
///
/// **主要组成部分:**
///
/// - **Header**: API 版本和响应类型元数据
///   - **Result**: 搜索结果负载,包含 ID 列表、计数、翻译信息等
///
/// @author linqibin
/// @since 0.1.0
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ESearchResponse {

  private final Header header;
  private final Result result;

  @JsonCreator
  public ESearchResponse(
      @JsonProperty("header") Header header, @JsonProperty("esearchresult") Result result) {
    this.header = header;
    this.result = result;
  }

  /// 获取 PubMed 返回的响应头块。
  ///
  /// @return 响应头
  public Header header() {
    return header;
  }

  /// 获取主要搜索结果负载。
  ///
  /// @return 结构化结果视图
  public Result result() {
    return result;
  }

  /// ESearch 端点返回的元数据响应头。
  ///
  /// 字段说明:
  ///
  /// @param type 响应类型指示器
  /// @param version ESearch API 版本
  /// @author linqibin
  /// @since 0.1.0
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Header(
      @JsonProperty("type") String type, @JsonProperty("version") String version) {}

  /// 搜索结果负载,汇总标识符、翻译和警告信息。
  ///
  /// 字段说明:
  ///
  /// @param count 匹配查询的总记录数
  /// @param retMax 返回的最大记录数
  /// @param retStart 当前页的偏移量
  /// @param idList 调用返回的标识符列表
  /// @param translationSet PubMed 应用的翻译对
  /// @param translationStack 原始翻译堆栈节点
  /// @param webEnv 历史服务器 WebEnv 令牌
  /// @param queryKey 用于历史服务器重用的查询键
  /// @param queryTranslation 翻译后的查询字符串
  /// @param errorList 包含错误短语的错误列表包装器
  /// @param warnings 包含消息的警告包装器
  /// @author linqibin
  /// @since 0.1.0
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Result(
      @JsonProperty("count") int count,
      @JsonProperty("retmax") int retMax,
      @JsonProperty("retstart") int retStart,
      @JsonProperty("idlist") List<String> idList,
      @JsonProperty("translationset") List<Translation> translationSet,
      @JsonProperty("translationstack") List<JsonNode> translationStack,
      @JsonProperty("webenv") String webEnv,
      @JsonProperty("querykey") String queryKey,
      @JsonProperty("querytranslation") String queryTranslation,
      @JsonProperty("errorlist") ErrorList errorList,
      @JsonProperty("warnings") Warnings warnings) {

    public Result {
      idList = idList != null ? List.copyOf(idList) : Collections.emptyList();
      translationSet =
          translationSet != null ? List.copyOf(translationSet) : Collections.emptyList();
      translationStack =
          translationStack != null ? List.copyOf(translationStack) : Collections.emptyList();
    }

    /// PubMed 应用的查询翻译对。
    ///
    /// 字段说明:
    ///
    /// @param from 原始词条
    /// @param to 应用到查询的翻译词条
    /// @author linqibin
    /// @since 0.1.0
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Translation(@JsonProperty("from") String from, @JsonProperty("to") String to) {}

    /// PubMed 响应中的错误列表包装器。
    ///
    /// @param phrase 错误短语列表
    /// @author linqibin
    /// @since 0.1.0
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorList(@JsonProperty("phrase") List<String> phrase) {}

    /// PubMed 响应中的警告包装器。
    ///
    /// @param outputMessage 输出消息
    /// @param warning 警告消息
    /// @author linqibin
    /// @since 0.1.0
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Warnings(
        @JsonProperty("outputmessage") List<String> outputMessage,
        @JsonProperty("warning") List<String> warning) {}
  }
}
