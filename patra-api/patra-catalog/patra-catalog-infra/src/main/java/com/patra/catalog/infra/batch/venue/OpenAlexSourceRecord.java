package com.patra.catalog.infra.batch.venue;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/// OpenAlex Sources JSON 映射 POJO。
///
/// 完整映射 OpenAlex Sources 快照的 JSON 结构，用于反序列化 JSON Lines 文件。
///
/// **字段说明**：
///
/// - `id`: OpenAlex ID（完整 URL 格式，如 `https://openalex.org/S137773608`）
/// - `displayName`: 期刊名称
/// - `issnL`: Linking ISSN（标准化 ISSN）
/// - `issn`: 所有关联的 ISSN 列表
/// - `type`: 来源类型（journal, conference, repository 等）
/// - `countryCode`: 国家代码（ISO 3166-1 alpha-2）
/// - `isOa`: 是否开放获取
/// - `isInDoaj`: 是否在 DOAJ 中收录
/// - `summaryStats`: 汇总统计（2 年平均引用、h-index、i10-index）
/// - `countsByYear`: 年度统计（论文数、引用数）
/// - `apcPrices`: 文章处理费列表
/// - `societies`: 关联学会列表
///
/// **使用方式**：
///
/// ```java
/// ObjectMapper mapper = new ObjectMapper()
///     .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
///     .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
/// OpenAlexSourceRecord record = mapper.readValue(jsonLine, OpenAlexSourceRecord.class);
/// ```
///
/// @author linqibin
/// @since 0.1.0
public record OpenAlexSourceRecord(
    String id,
    String displayName,
    String issnL,
    List<String> issn,
    String type,
    String typeId,
    String countryCode,
    Boolean isOa,
    Boolean isInDoaj,
    String homepageUrl,
    Integer worksCount,
    Integer citedByCount,
    String worksApiUrl,
    Integer apcUsd,
    String hostOrganization,
    String hostOrganizationName,
    List<String> hostOrganizationLineage,
    SummaryStats summaryStats,
    Ids ids,
    List<CountsByYear> countsByYear,
    List<ApcPrice> apcPrices,
    List<Society> societies,
    String abbreviatedTitle,
    List<String> alternateTitles,
    String createdDate,
    String updatedDate) {

  /// 主构造函数 - 用于 Jackson 反序列化。
  public OpenAlexSourceRecord {}

  /// 提取 OpenAlex ID 后缀。
  ///
  /// 从完整 URL 中提取 ID，如 `https://openalex.org/S137773608` → `S137773608`。
  ///
  /// @return OpenAlex ID 后缀，如果 id 为 null 则返回 null
  public String extractOpenAlexId() {
    if (id == null) {
      return null;
    }
    return id.replace("https://openalex.org/", "");
  }

  /// 汇总统计数据。
  ///
  /// @param twoYrMeanCitedness 2 年平均引用数
  /// @param hIndex h-index
  /// @param i10Index i10-index
  public record SummaryStats(
      @JsonProperty("2yr_mean_citedness") Double twoYrMeanCitedness,
      Integer hIndex,
      Integer i10Index) {}

  /// 标识符集合。
  ///
  /// @param openalex OpenAlex ID
  /// @param issnL Linking ISSN
  /// @param issn ISSN 列表
  /// @param wikidata Wikidata ID
  public record Ids(String openalex, String issnL, List<String> issn, String wikidata) {}

  /// 年度统计数据。
  ///
  /// @param year 年份
  /// @param worksCount 论文数
  /// @param citedByCount 被引用数
  public record CountsByYear(Integer year, Integer worksCount, Integer citedByCount) {}

  /// 文章处理费。
  ///
  /// @param price 价格
  /// @param currency 货币代码
  public record ApcPrice(Integer price, String currency) {}

  /// 关联学会。
  ///
  /// @param url 学会网址
  /// @param organization 学会名称
  public record Society(String url, String organization) {}
}
