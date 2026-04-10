package com.patra.catalog.infra.adapter.integration.letpub;

import com.patra.catalog.domain.port.enrichment.LetPubVenueData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.web.client.RestClient;

/// LetPub 期刊数据爬取客户端。
///
/// 通过 ISSN 在 [LetPub](https://www.letpub.com.cn) 搜索并爬取期刊评价数据，
/// 包括 JCR/CAS 分区、审稿速度、APC 费用等。
///
/// **两步爬取流程**：
///
/// 1. `searchByIssn(issn)` → GET 搜索页 → 提取 `journalid`
/// 2. `scrapeDetail(journalId)` → GET 详情页 → 解析全部字段
///
/// **反爬策略**：
///
/// - 每次请求前等待 `baseDelay`（默认 8s）+ 随机抖动（0-3s）
/// - 限流检测：响应体 < 1500 字节且包含 "速度过快"
/// - 限流时指数退避：30s → 60s → 120s → 240s
///
/// **注意**：此类非 Spring 组件，由 Boot 层 `LetPubConfiguration` 创建为 Bean。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class LetPubScrapingClient {

  /// LetPub 搜索 URL。
  static final String SEARCH_URL = "https://www.letpub.com.cn/index.php";

  /// 限流检测阈值：响应体小于此字节数时检查是否被限流。
  static final int RATE_LIMIT_THRESHOLD = 1500;

  /// 限流关键词。
  static final String RATE_LIMIT_KEYWORD = "速度过快";

  /// 最大限流重试次数。
  static final int MAX_RATE_LIMIT_RETRIES = 4;

  /// journalid 提取正则（排除 journalid=0）。
  private static final Pattern JOURNAL_ID_PATTERN =
      Pattern.compile("journalid=(\\d+).*?view=detail");

  /// CAS 版本提取正则。
  private static final Pattern CAS_VERSION_PATTERN =
      Pattern.compile("中国科学院期刊分区\\s*（\\s*(20\\d{2}年\\d{1,2}月[^）]*)）");

  private final RestClient restClient;
  private final long baseDelayMs;
  private final long jitterMs;

  /// 生产构造器。
  ///
  /// @param restClient HTTP 客户端
  public LetPubScrapingClient(RestClient restClient) {
    this(restClient, 8000, 3000);
  }

  /// 可配置延迟的构造器。
  ///
  /// 用于隧道代理模式（降低延迟）或测试场景。
  ///
  /// @param restClient HTTP 客户端
  /// @param baseDelayMs 基础延迟毫秒数
  /// @param jitterMs 随机抖动毫秒数
  public LetPubScrapingClient(RestClient restClient, long baseDelayMs, long jitterMs) {
    this.restClient = restClient;
    this.baseDelayMs = baseDelayMs;
    this.jitterMs = jitterMs;
  }

  /// 通过 ISSN 查找 LetPub 期刊数据（完整两步爬取）。
  ///
  /// @param issn ISSN 标识符
  /// @return 期刊评价数据，未找到返回 empty
  public Optional<LetPubVenueData> findByIssn(String issn) {
    if (issn == null || issn.isBlank()) {
      return Optional.empty();
    }

    try {
      // Step 1: 搜索获取 journalId
      delay();
      String searchHtml = fetchSearchPage(issn);
      if (isRateLimited(searchHtml)) {
        searchHtml = retryWithBackoff(() -> fetchSearchPage(issn));
      }

      Optional<String> journalId = parseSearchResult(searchHtml);
      if (journalId.isEmpty()) {
        log.debug("LetPub 未找到 ISSN {} 对应的期刊", issn);
        return Optional.empty();
      }

      // Step 2: 爬取详情页
      delay();
      String detailHtml = fetchDetailPage(journalId.get());
      if (isRateLimited(detailHtml)) {
        String jid = journalId.get();
        detailHtml = retryWithBackoff(() -> fetchDetailPage(jid));
      }

      LetPubVenueData data = parseDetailPage(detailHtml, journalId.get());
      log.info(
          "LetPub 成功爬取 ISSN {} → journalId={}, name={}", issn, journalId.get(), data.letPubName());
      return Optional.of(data);

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("LetPub 爬取被中断: ISSN={}", issn);
      return Optional.empty();
    } catch (Exception e) {
      log.warn("LetPub 爬取失败: ISSN={}, error={}", issn, e.getMessage());
      throw new LetPubScrapingException("LetPub 爬取失败: ISSN=" + issn, e);
    }
  }

  // ========== HTTP 调用 ==========

  /// 获取搜索结果页面。
  String fetchSearchPage(String issn) {
    return restClient
        .get()
        .uri(
            SEARCH_URL
                + "?page=journalapp&view=search&searchname=&searchissn={issn}"
                + "&searchfield=&searchimpactlow=&searchimpacthigh="
                + "&searchsci498telecomkey=&searchcategory1=&searchcategory2="
                + "&searchjcrkind=&searchopenaccess=&searchsort=relevance",
            issn)
        .retrieve()
        .body(String.class);
  }

  /// 获取详情页面。
  String fetchDetailPage(String journalId) {
    return restClient
        .get()
        .uri(SEARCH_URL + "?journalid={journalId}&page=journalapp&view=detail", journalId)
        .retrieve()
        .body(String.class);
  }

  // ========== HTML 解析 ==========

  /// 从搜索结果 HTML 中提取 journalId。
  ///
  /// 查找包含 `view=detail` 的链接中的 `journalid` 参数，
  /// 排除 `journalid=0`（评论按钮噪音）。
  ///
  /// @param html 搜索结果页 HTML
  /// @return journalId，未找到返回 empty
  Optional<String> parseSearchResult(String html) {
    if (html == null || html.isBlank()) {
      return Optional.empty();
    }

    Matcher matcher = JOURNAL_ID_PATTERN.matcher(html);
    while (matcher.find()) {
      String id = matcher.group(1);
      if (!"0".equals(id)) {
        return Optional.of(id);
      }
    }
    return Optional.empty();
  }

  /// 解析详情页 HTML，提取全部期刊评价字段。
  ///
  /// @param html 详情页 HTML
  /// @param journalId LetPub 内部 ID
  /// @return 期刊评价数据
  LetPubVenueData parseDetailPage(String html, String journalId) {
    Document doc = Jsoup.parse(html);
    String rawHtml = html; // 保留原始 HTML 用于正则解析复杂结构

    LetPubVenueData.LetPubVenueDataBuilder builder =
        LetPubVenueData.builder().letPubJournalId(journalId);

    // 期刊名：从可见的 h1 提取（跳过 display:none 的品牌标识），去除 "期刊收藏夹" 后缀
    doc.select("h1").stream()
        .filter(h1 -> !h1.attr("style").matches("(?i).*display\\s*:\\s*none.*"))
        .findFirst()
        .ifPresent(
            h1 -> {
              String name = h1.text().split("期刊收藏夹")[0].trim();
              builder.letPubName(name);
            });

    // 基本信息：label/value td 对
    builder.researchDirection(findFieldValue(doc, "涉及的研究方向"));
    builder.country(findFieldValue(doc, "出版国家或地区"));
    builder.language(findFieldValue(doc, "出版语言"));
    builder.frequency(findFieldValue(doc, "出版周期"));

    String startYearStr = findFieldValue(doc, "出版年份");
    builder.startYear(parseInteger(startYearStr));

    String articlesStr = findFieldValue(doc, "年文章数");
    builder.articlesPerYear(parseInteger(articlesStr.replace(",", "")));

    builder.goldOaPercent(findFieldValue(doc, "Gold OA文章占比"));
    builder.researchArticlePercent(findFieldValue(doc, "研究类文章占比"));

    // JCR 分区
    parseJcrPartition(rawHtml, builder);

    // CAS 分区
    parseCasPartition(rawHtml, doc, builder);

    // 预警名单
    parseWarningList(rawHtml, builder);

    // 审稿速度
    parseReviewSpeed(rawHtml, builder);

    // 录用比例
    parseAcceptanceRate(rawHtml, builder);

    // APC 费用
    parseApc(rawHtml, builder);

    // 收录情况
    parseIndexedIn(rawHtml, builder);

    // 影响因子趋势
    parseImpactFactorTrend(rawHtml, builder);

    // 五年影响因子
    builder.fiveYearImpactFactor(parseDouble(findFieldValue(doc, "五年影响因子")));

    return builder.build();
  }

  /// 判断响应是否为限流页面。
  ///
  /// @param body 响应体
  /// @return true 表示被限流
  boolean isRateLimited(String body) {
    if (body == null || body.isEmpty()) {
      return false;
    }
    return body.length() < RATE_LIMIT_THRESHOLD && body.contains(RATE_LIMIT_KEYWORD);
  }

  // ========== 内部解析方法 ==========

  /// 在文档中查找 label 对应的值（td label → 下一个兄弟 td 的文本）。
  private String findFieldValue(Document doc, String label) {
    for (Element td : doc.select("td")) {
      if (td.text().contains(label)) {
        Element nextTd = td.nextElementSibling();
        if (nextTd != null && "td".equals(nextTd.tagName())) {
          return cleanText(nextTd.text());
        }
      }
    }
    return "";
  }

  /// 解析 JCR 分区（JIF + JCI 指标）。
  private void parseJcrPartition(String html, LetPubVenueData.LetPubVenueDataBuilder builder) {
    // JIF 分区
    int jifPos = html.indexOf("按JIF指标学科分区");
    if (jifPos < 0) {
      return;
    }

    int jciPos = html.indexOf("按JCI指标学科分区", jifPos);
    String jifSection =
        jciPos > jifPos
            ? html.substring(jifPos, jciPos)
            : html.substring(jifPos, Math.min(jifPos + 3000, html.length()));

    Pattern jifPattern =
        Pattern.compile(
            "学科：(.*?)</td>.*?<td[^>]*>(SCIE|SSCI|ESCI|AHCI)</td>\\s*<td[^>]*>(Q[1-4])</td>\\s*<td[^>]*>([\\d/]+)",
            Pattern.DOTALL);
    Matcher jifMatcher = jifPattern.matcher(jifSection);
    if (jifMatcher.find()) {
      builder.jcrSubject(cleanHtml(jifMatcher.group(1)));
      builder.jcrCollection(jifMatcher.group(2));
      builder.jifQuartile(jifMatcher.group(3));
      builder.jifRank(jifMatcher.group(4));
    }

    // JCI 分区
    if (jciPos > 0) {
      String jciSection = html.substring(jciPos, Math.min(jciPos + 3000, html.length()));
      Pattern jciPattern =
          Pattern.compile(
              "学科：.*?</td>.*?<td[^>]*>(?:SCIE|SSCI|ESCI|AHCI)</td>\\s*<td[^>]*>(Q[1-4])</td>\\s*<td[^>]*>([\\d/]+)",
              Pattern.DOTALL);
      Matcher jciMatcher = jciPattern.matcher(jciSection);
      if (jciMatcher.find()) {
        builder.jciQuartile(jciMatcher.group(1));
        builder.jciRank(jciMatcher.group(2));
      }
    }
  }

  /// 解析 CAS 中科院分区。
  private void parseCasPartition(
      String html, Document doc, LetPubVenueData.LetPubVenueDataBuilder builder) {
    // 提取版本号
    Matcher versionMatcher = CAS_VERSION_PATTERN.matcher(html);
    if (!versionMatcher.find()) {
      return;
    }
    builder.casVersion(cleanText(versionMatcher.group(1)));

    // 找到分区数据区域
    int trendPos = html.indexOf("点击查看中国科学院期刊分区趋势图", versionMatcher.start());
    if (trendPos < 0) {
      return;
    }

    String casSection = html.substring(trendPos, Math.min(trendPos + 2000, html.length()));
    String casText = cleanHtml(casSection);

    // 大类+分区
    Pattern majorPattern = Pattern.compile("大类学科小类学科Top期刊综述期刊\\s*(\\S+?)\\s+(\\d)区");
    Matcher majorMatcher = majorPattern.matcher(casText);
    if (majorMatcher.find()) {
      builder.casMajorCategory(majorMatcher.group(1));
      builder.casMajorQuartile(majorMatcher.group(2) + "区");
    }

    // 小类学科
    Pattern minorPattern =
        Pattern.compile(
            "<td[^>]*>([A-Z][A-Z, &;]+.*?)</td>\\s*<td[^>]*>(\\d区.*?)</td>", Pattern.DOTALL);
    Matcher minorMatcher = minorPattern.matcher(casSection);
    if (minorMatcher.find()) {
      builder.casMinorSubject(cleanHtml(minorMatcher.group(1)).trim());
      String quartileRaw = cleanHtml(minorMatcher.group(2)).trim();
      Matcher qMatcher = Pattern.compile("(\\d区)").matcher(quartileRaw);
      builder.casMinorQuartile(qMatcher.find() ? qMatcher.group(1) : quartileRaw);
    }

    // Top 期刊 / 综述期刊
    Pattern boolPattern = Pattern.compile("<td[^>]*>\\s*(是|否)\\s*</td>");
    Matcher boolMatcher = boolPattern.matcher(casSection);
    List<String> boolValues = new ArrayList<>();
    while (boolMatcher.find()) {
      boolValues.add(boolMatcher.group(1));
    }
    if (boolValues.size() >= 2) {
      builder.casTopJournal("是".equals(boolValues.get(0)));
      builder.casReviewJournal("是".equals(boolValues.get(1)));
    } else if (boolValues.size() == 1) {
      builder.casTopJournal("是".equals(boolValues.getFirst()));
    }
  }

  /// 解析预警名单状态。
  private void parseWarningList(String html, LetPubVenueData.LetPubVenueDataBuilder builder) {
    Pattern pattern = Pattern.compile("(20\\d{2}年\\d{1,2}月发布的\\d{4}版：[^<\\n]{2,40})");
    Matcher matcher = pattern.matcher(html);
    if (matcher.find()) {
      builder.warningListStatus(cleanText(matcher.group(1)));
    }
  }

  /// 解析审稿速度。
  private void parseReviewSpeed(String html, LetPubVenueData.LetPubVenueDataBuilder builder) {
    Pattern pattern = Pattern.compile("平均审稿速度.*?</td>\\s*<td[^>]*>(.*?)</td>", Pattern.DOTALL);
    Matcher matcher = pattern.matcher(html);
    if (matcher.find()) {
      String text = cleanHtml(matcher.group(1)).trim();

      Pattern officialPattern = Pattern.compile("期刊官网数据：(.*?)(?:网友|$)");
      Matcher officialMatcher = officialPattern.matcher(text);
      if (officialMatcher.find()) {
        builder.reviewSpeedOfficial(officialMatcher.group(1).trim());
      }

      Pattern userPattern = Pattern.compile("网友分享经验：\\s*(.*?)$");
      Matcher userMatcher = userPattern.matcher(text);
      if (userMatcher.find()) {
        builder.reviewSpeedUser(userMatcher.group(1).trim());
      }
    }
  }

  /// 解析录用比例。
  private void parseAcceptanceRate(String html, LetPubVenueData.LetPubVenueDataBuilder builder) {
    Pattern pattern = Pattern.compile("平均录用比例.*?网友分享经验：\\s*([\\d.]+%)", Pattern.DOTALL);
    Matcher matcher = pattern.matcher(html);
    if (matcher.find()) {
      builder.acceptanceRate(matcher.group(1));
    }
  }

  /// 解析 APC 费用。
  private void parseApc(String html, LetPubVenueData.LetPubVenueDataBuilder builder) {
    Pattern pattern = Pattern.compile("APC文章处理费信息.*?</td>\\s*<td[^>]*>(.*?)</td>", Pattern.DOTALL);
    Matcher matcher = pattern.matcher(html);
    if (matcher.find()) {
      String text = cleanHtml(matcher.group(1)).trim();
      builder.apcInfo(text.length() > 200 ? text.substring(0, 200) : text);
    }
  }

  /// 解析收录情况。
  private void parseIndexedIn(String html, LetPubVenueData.LetPubVenueDataBuilder builder) {
    Pattern pattern = Pattern.compile("SCI期刊收录.*?</td>\\s*<td[^>]*>(.*?)</td>", Pattern.DOTALL);
    Matcher matcher = pattern.matcher(html);
    if (matcher.find()) {
      String text = cleanHtml(matcher.group(1)).trim();
      List<String> databases =
          List.of(text.split(",\\s*")).stream()
              .map(String::trim)
              .filter(s -> !s.isEmpty())
              .toList();
      builder.indexedIn(databases);
    }
  }

  /// 从 ECharts `showecharts_if_trend()` 函数中解析影响因子趋势数据。
  ///
  /// 提取 `xAxis.data`（年份数组）和 `series[0].data`（IF 值数组），
  /// 年份格式从 `"2024-2025年度"` 简化为 `"2024-2025"`。
  private void parseImpactFactorTrend(String html, LetPubVenueData.LetPubVenueDataBuilder builder) {
    // 提取 showecharts_if_trend 函数体：从函数定义到 setOption 调用
    int funcPos = html.indexOf("function showecharts_if_trend");
    if (funcPos < 0) {
      return;
    }
    int endPos = html.indexOf("setOption", funcPos);
    if (endPos < 0) {
      return;
    }
    String funcSection = html.substring(funcPos, endPos);

    // 提取年份数组：data : ['2015-2016年度','2016-2017年度',...]
    Pattern yearPattern = Pattern.compile("xAxis.*?data\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);
    Matcher yearMatcher = yearPattern.matcher(funcSection);
    if (!yearMatcher.find()) {
      return;
    }
    List<String> years = extractQuotedStrings(yearMatcher.group(1));

    // 提取 IF 值数组：data : [38.138,40.137,...]
    Pattern valuePattern =
        Pattern.compile("series.*?data\\s*:\\s*\\[([\\d.,\\s]+)]", Pattern.DOTALL);
    Matcher valueMatcher = valuePattern.matcher(funcSection);
    if (!valueMatcher.find()) {
      return;
    }
    String[] valueTokens = valueMatcher.group(1).split(",");

    if (years.size() != valueTokens.length) {
      log.warn("IF 趋势数据年份({})与数值({})数量不匹配", years.size(), valueTokens.length);
      return;
    }

    Map<String, Double> trend = new LinkedHashMap<>();
    for (int i = 0; i < years.size(); i++) {
      String year = years.get(i).replace("年度", "").trim();
      Double value = parseDouble(valueTokens[i].trim());
      if (value != null) {
        trend.put(year, value);
      }
    }
    builder.impactFactorTrend(trend);
  }

  /// 从逗号分隔的引号字符串中提取值列表。
  private static List<String> extractQuotedStrings(String raw) {
    List<String> result = new ArrayList<>();
    Matcher m = Pattern.compile("'([^']*)'").matcher(raw);
    while (m.find()) {
      result.add(m.group(1));
    }
    return result;
  }

  /// 安全解析 Double。
  private static Double parseDouble(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    try {
      return Double.parseDouble(text.replaceAll("[^\\d.]", ""));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  // ========== 反爬/重试 ==========

  /// 等待基础延迟 + 随机抖动。
  private void delay() throws InterruptedException {
    if (baseDelayMs > 0) {
      long totalDelay = baseDelayMs + (long) (Math.random() * jitterMs);
      Thread.sleep(totalDelay);
    }
  }

  /// 限流时指数退避重试。
  private String retryWithBackoff(ThrowingSupplier<String> fetcher) throws Exception {
    long[] backoffs = {30_000, 60_000, 120_000, 240_000};
    for (int i = 0; i < MAX_RATE_LIMIT_RETRIES; i++) {
      log.warn("LetPub 限流检测，第 {} 次退避等待 {}s", i + 1, backoffs[i] / 1000);
      Thread.sleep(backoffs[i]);
      String result = fetcher.get();
      if (!isRateLimited(result)) {
        return result;
      }
    }
    throw new LetPubScrapingException("LetPub 限流重试超过最大次数: " + MAX_RATE_LIMIT_RETRIES);
  }

  // ========== 工具方法 ==========

  /// 清理 HTML 标签，返回纯文本。
  private static String cleanHtml(String html) {
    return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
  }

  /// 清理文本中多余空白。
  private static String cleanText(String text) {
    if (text == null) {
      return "";
    }
    return text.replaceAll("\\s+", " ").trim();
  }

  /// 安全解析整数。
  private static Integer parseInteger(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    try {
      return Integer.parseInt(text.replaceAll("[^\\d]", ""));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /// 可抛异常的 Supplier。
  @FunctionalInterface
  interface ThrowingSupplier<T> {
    /// 获取值。
    ///
    /// @return 值
    /// @throws Exception 如果获取失败
    T get() throws Exception;
  }
}
