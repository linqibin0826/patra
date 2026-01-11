package com.patra.catalog.infra.adapter.batch.venue;

import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.enums.VenueIdentifierType;
import com.patra.catalog.domain.model.enums.VenueType;
import com.patra.catalog.domain.model.vo.venue.CitationMetrics;
import com.patra.catalog.domain.model.vo.venue.HostOrganization;
import com.patra.catalog.domain.model.vo.venue.OpenAccessInfo;
import com.patra.catalog.domain.model.vo.venue.PublicationProfile;
import com.patra.catalog.domain.model.vo.venue.Society;
import com.patra.catalog.domain.model.vo.venue.VenuePublicationStats;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/// OpenAlex Sources JSON Lines 解析器。
///
/// 从 .gz 压缩的 JSON Lines 文件解析 OpenAlex Sources 数据，
/// 并转换为 `VenueParseResult`（包含 `VenueAggregate` 和年度指标）。
///
/// **DDD 嵌入式值对象设计**：
///
/// 解析器直接将值对象嵌入到聚合根中：
/// - `PublicationProfile` - 出版概况
/// - `CitationMetrics` - 引用指标
/// - `OpenAccessInfo` - 开放获取信息（合并 OA 状态 + APC 定价）
/// - `List<Society>` - 关联学会
///
/// **数据格式**：
///
/// - 输入：.gz 压缩的 JSON Lines 文件（每行一个 JSON 对象）
/// - 输出：`Stream<VenueParseResult>` 流式处理
///
/// **转换规则**：
///
/// - `id` → 提取后缀作为 `openalexId`
/// - `type` → 通过 `VenueType.fromOpenAlexType()` 转换
/// - `summary_stats` + `works_count` + `cited_by_count` → 转换为 `CitationMetrics`
/// - `counts_by_year` → 转换为 `VenuePublicationStats` 列表（独立于聚合根）
/// - `is_oa` + `is_in_doaj` + `apc_prices` + `apc_usd` → 转换为 `OpenAccessInfo`
/// - `societies` → 转换为 `Society` 列表
/// - `host_organization*` → 转换为 `HostOrganization`
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
public class OpenAlexSourceParser {

  private final ObjectMapper objectMapper;

  /// 创建解析器实例。
  public OpenAlexSourceParser() {
    this.objectMapper =
        JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
  }

  /// 解析 .gz 压缩的 JSON Lines 输入流。
  ///
  /// **注意**：返回的 Stream 在使用完毕后需要关闭以释放资源。
  ///
  /// @param gzipInputStream .gz 压缩的输入流
  /// @return VenueParseResult 流（包含聚合根和年度指标）
  /// @throws IOException 读取或解析失败时
  public Stream<VenueParseResult> parse(InputStream gzipInputStream) throws IOException {
    GZIPInputStream gzis = new GZIPInputStream(gzipInputStream);
    BufferedReader reader = new BufferedReader(new InputStreamReader(gzis, StandardCharsets.UTF_8));

    return reader
        .lines()
        .filter(line -> line != null && !line.isBlank())
        .map(this::parseLine)
        .filter(Objects::nonNull)
        .map(this::toParseResult)
        .onClose(
            () -> {
              try {
                reader.close();
              } catch (IOException e) {
                log.warn("关闭 reader 失败", e);
              }
            });
  }

  /// 解析单行 JSON。
  ///
  /// @param line JSON 行
  /// @return 解析的记录，解析失败时返回 null
  private OpenAlexSourceRecord parseLine(String line) {
    try {
      return objectMapper.readValue(line, OpenAlexSourceRecord.class);
    } catch (Exception e) {
      log.warn(
          "解析 JSON 行失败：{}，错误：{}", line.substring(0, Math.min(100, line.length())), e.getMessage());
      return null;
    }
  }

  /// 将 OpenAlexSourceRecord 转换为 VenueParseResult。
  ///
  /// **DDD 嵌入式值对象设计**：
  ///
  /// 聚合根通过 `with*` 方法嵌入所有值对象：
  /// - publicationProfile: 出版概况
  /// - citationMetrics: 引用指标
  /// - openAccess: 开放获取信息
  /// - affiliatedSocieties: 关联学会
  ///
  /// @param record OpenAlex 源记录
  /// @return VenueParseResult（包含聚合根和年度指标）
  VenueParseResult toParseResult(OpenAlexSourceRecord record) {
    // 1. 提取基础字段
    String openalexId = record.extractOpenAlexId();
    VenueType venueType = VenueType.fromOpenAlexType(record.type());

    // 2. 创建聚合根
    VenueAggregate aggregate =
        VenueAggregate.fromOpenAlex(openalexId, venueType, record.displayName());

    // 3. 添加标识符（ISSN + ISSN-L）
    addIdentifiers(aggregate, record);

    // 4. 嵌入出版概况
    PublicationProfile profile = buildPublicationProfile(record);
    if (profile != null) {
      aggregate.withPublicationProfile(profile);
    }

    // 5. 嵌入引用指标
    CitationMetrics metrics = buildCitationMetrics(record);
    if (metrics != null) {
      aggregate.withCitationMetrics(metrics);
    }

    // 6. 嵌入开放获取信息（合并 OA 状态 + APC）
    OpenAccessInfo openAccessInfo = buildOpenAccessInfo(record);
    if (openAccessInfo != null) {
      aggregate.withOpenAccess(openAccessInfo);
    }

    // 7. 嵌入关联学会
    List<Society> societies = buildSocieties(record);
    if (societies != null && !societies.isEmpty()) {
      aggregate.withAffiliatedSocieties(societies);
    }

    // 8. 构建年度指标（独立存储，非嵌入式）
    List<VenuePublicationStats> yearlyMetrics = buildYearlyMetrics(record);

    return new VenueParseResult(aggregate, yearlyMetrics);
  }

  /// 构建出版概况值对象。
  ///
  /// 将 OpenAlex 记录中的出版相关字段封装到 PublicationProfile 中。
  private PublicationProfile buildPublicationProfile(OpenAlexSourceRecord record) {
    // 构建宿主机构
    HostOrganization hostOrganization = null;
    if (record.hostOrganization() != null && record.hostOrganizationName() != null) {
      String hostId = extractOpenAlexId(record.hostOrganization());
      hostOrganization =
          HostOrganization.of(
              hostId, record.hostOrganizationName(), record.hostOrganizationLineage());
    }

    // 如果没有任何数据，返回 null
    if (record.abbreviatedTitle() == null
        && (record.alternateTitles() == null || record.alternateTitles().isEmpty())
        && record.homepageUrl() == null
        && hostOrganization == null
        && record.countryCode() == null) {
      return null;
    }

    return PublicationProfile.builder()
        .abbreviatedTitle(record.abbreviatedTitle())
        .alternateTitles(record.alternateTitles())
        .homepageUrl(record.homepageUrl())
        .hostOrganization(hostOrganization)
        .countryCode(record.countryCode())
        .build();
  }

  /// 添加标识符（ISSN + ISSN-L）。
  ///
  /// 将 ISSN-L 和 ISSN 列表作为标识符添加到聚合根。
  private void addIdentifiers(VenueAggregate aggregate, OpenAlexSourceRecord record) {
    // 添加 ISSN-L 标识符
    if (record.issnL() != null && !record.issnL().isBlank() && isValidIssn(record.issnL())) {
      aggregate.addIdentifier(VenueIdentifierType.ISSN_L, record.issnL());
    }

    // 添加 ISSN 标识符
    if (record.issn() != null) {
      for (String issn : record.issn()) {
        if (issn != null && !issn.isBlank() && isValidIssn(issn)) {
          aggregate.addIdentifier(VenueIdentifierType.ISSN, issn);
        }
      }
    }
  }

  /// 构建引用指标值对象。
  private CitationMetrics buildCitationMetrics(OpenAlexSourceRecord record) {
    Integer hIndex = null;
    Integer i10Index = null;
    BigDecimal twoYrMeanCitedness = null;

    if (record.summaryStats() != null) {
      hIndex = record.summaryStats().hIndex();
      i10Index = record.summaryStats().i10Index();
      if (record.summaryStats().twoYrMeanCitedness() != null) {
        twoYrMeanCitedness = BigDecimal.valueOf(record.summaryStats().twoYrMeanCitedness());
      }
    }

    if (record.worksCount() != null || record.citedByCount() != null) {
      return CitationMetrics.of(
          record.worksCount(), record.citedByCount(), hIndex, i10Index, twoYrMeanCitedness);
    }

    return null;
  }

  /// 构建开放获取信息值对象。
  ///
  /// 合并 OA 状态（isOa、isInDoaj）和 APC 定价信息到统一的 OpenAccessInfo。
  ///
  /// **注意**：当源数据明确提供了 isOa 或 isInDoaj 字段（即使值为 false）时，
  /// 也会创建 OpenAccessInfo 对象，因为 false 表示"确认不是 OA"，与"未知"是不同的。
  private OpenAccessInfo buildOpenAccessInfo(OpenAlexSourceRecord record) {
    Boolean isOaRaw = record.isOa();
    Boolean isInDoajRaw = record.isInDoaj();
    Integer apcUsd = record.apcUsd();
    List<OpenAccessInfo.ApcPrice> apcPrices = null;

    if (record.apcPrices() != null && !record.apcPrices().isEmpty()) {
      apcPrices =
          record.apcPrices().stream()
              .map(p -> OpenAccessInfo.ApcPrice.of(p.price(), p.currency()))
              .toList();
    }

    // 如果所有 OA 相关字段都是 null（未提供），返回 null
    // 注意：isOa=false 或 isInDoaj=false 也是有意义的信息，需要保留
    boolean hasOaData =
        isOaRaw != null || isInDoajRaw != null || apcUsd != null || apcPrices != null;
    if (!hasOaData) {
      return null;
    }

    boolean isOa = isOaRaw != null && isOaRaw;
    boolean isInDoaj = isInDoajRaw != null && isInDoajRaw;
    return OpenAccessInfo.of(isOa, isInDoaj, null, apcUsd, apcPrices);
  }

  /// 构建学会列表。
  private List<Society> buildSocieties(OpenAlexSourceRecord record) {
    if (record.societies() == null || record.societies().isEmpty()) {
      return null;
    }

    return record.societies().stream()
        .filter(s -> s.organization() != null && !s.organization().isBlank())
        .map(s -> Society.of(s.url(), s.organization()))
        .toList();
  }

  /// 构建年度指标列表。
  ///
  /// 过滤规则：跳过年份为 null 或不在 1900-2100 范围内的记录（无效统计数据）
  private List<VenuePublicationStats> buildYearlyMetrics(OpenAlexSourceRecord record) {
    if (record.countsByYear() == null || record.countsByYear().isEmpty()) {
      return null;
    }

    return record.countsByYear().stream()
        .filter(c -> c.year() != null && c.year() >= 1900 && c.year() <= 2100)
        .map(
            c ->
                VenuePublicationStats.create(
                    c.year(),
                    c.worksCount() != null ? c.worksCount() : 0,
                    c.citedByCount() != null ? c.citedByCount() : 0))
        .toList();
  }

  /// ISSN 格式正则表达式（与 VenueIdentifier 保持一致）。
  private static final String ISSN_PATTERN = "\\d{4}-\\d{3}[\\dXx]";

  /// 验证 ISSN 格式是否有效。
  ///
  /// @param issn ISSN 值
  /// @return true 如果格式有效
  private boolean isValidIssn(String issn) {
    return issn.matches(ISSN_PATTERN);
  }

  /// 从 OpenAlex URL 提取 ID 后缀。
  private String extractOpenAlexId(String url) {
    if (url == null) {
      return null;
    }
    return url.replace("https://openalex.org/", "");
  }
}
