package com.patra.catalog.infra.batch.venue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.enums.VenueIdentifierType;
import com.patra.catalog.domain.model.enums.VenueType;
import com.patra.catalog.domain.model.vo.venue.ApcInfo;
import com.patra.catalog.domain.model.vo.venue.HostOrganization;
import com.patra.catalog.domain.model.vo.venue.Society;
import com.patra.catalog.domain.model.vo.venue.VenueDetail;
import com.patra.catalog.domain.model.vo.venue.VenuePublicationStats;
import com.patra.catalog.domain.model.vo.venue.VenueStats;
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

/// OpenAlex Sources JSON Lines 解析器。
///
/// 从 .gz 压缩的 JSON Lines 文件解析 OpenAlex Sources 数据，
/// 并转换为 `VenueParseResult`（包含 `VenueAggregate` 和年度指标）。
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
/// - `summary_stats` → 转换为 `VenueStats`
/// - `counts_by_year` → 转换为 `VenuePublicationStats` 列表（独立于聚合根）
/// - `apc_prices` + `apc_usd` → 转换为 `ApcInfo`
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
        new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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
  /// **CQRS 最小聚合设计**：
  ///
  /// - 聚合根只包含核心字段：id, venueType, displayName, identifiers, provenance
  /// - 补充数据（detail, stats, apc, societies）独立返回，由调用方分别保存
  ///
  /// @param record OpenAlex 源记录
  /// @return VenueParseResult（包含聚合根和所有补充数据）
  VenueParseResult toParseResult(OpenAlexSourceRecord record) {
    // 1. 提取基础字段
    String openalexId = record.extractOpenAlexId();
    VenueType venueType = VenueType.fromOpenAlexType(record.type());

    // 2. 创建聚合根（只包含核心字段）
    VenueAggregate aggregate =
        VenueAggregate.fromOpenAlex(openalexId, venueType, record.displayName());

    // 3. 添加标识符（ISSN + ISSN-L）
    addIdentifiers(aggregate, record);

    // 4. 构建详情值对象（包含所有非核心属性）
    VenueDetail detail = buildVenueDetail(record);

    // 5. 构建统计快照
    VenueStats stats = buildVenueStats(record);

    // 6. 构建 APC 信息
    ApcInfo apcInfo = buildApcInfo(record);

    // 7. 构建学会列表
    List<Society> societies = buildSocieties(record);

    // 8. 构建年度指标
    List<VenuePublicationStats> yearlyMetrics = buildYearlyMetrics(record);

    return new VenueParseResult(aggregate, detail, stats, apcInfo, societies, yearlyMetrics);
  }

  /// 构建详情值对象。
  ///
  /// 将原聚合根的非核心字段封装到 VenueDetail 中。
  private VenueDetail buildVenueDetail(OpenAlexSourceRecord record) {
    // 构建宿主机构
    HostOrganization hostOrganization = null;
    if (record.hostOrganization() != null && record.hostOrganizationName() != null) {
      String hostId = extractOpenAlexId(record.hostOrganization());
      hostOrganization =
          HostOrganization.of(
              hostId, record.hostOrganizationName(), record.hostOrganizationLineage());
    }

    return VenueDetail.builder()
        .abbreviatedTitle(record.abbreviatedTitle())
        .alternateTitles(record.alternateTitles())
        .homepageUrl(record.homepageUrl())
        .hostOrganization(hostOrganization)
        .countryCode(record.countryCode())
        .isOa(record.isOa() != null ? record.isOa() : false)
        .isInDoaj(record.isInDoaj() != null ? record.isInDoaj() : false)
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

  /// 构建统计快照。
  private VenueStats buildVenueStats(OpenAlexSourceRecord record) {
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
      return VenueStats.of(
          record.worksCount(), record.citedByCount(), hIndex, i10Index, twoYrMeanCitedness);
    }

    return null;
  }

  /// 构建 APC 信息。
  private ApcInfo buildApcInfo(OpenAlexSourceRecord record) {
    if (record.apcUsd() == null && (record.apcPrices() == null || record.apcPrices().isEmpty())) {
      return null;
    }

    List<ApcInfo.ApcPrice> prices =
        record.apcPrices() != null
            ? record.apcPrices().stream()
                .map(p -> ApcInfo.ApcPrice.of(p.price(), p.currency()))
                .toList()
            : List.of();

    return ApcInfo.of(record.apcUsd(), prices);
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
