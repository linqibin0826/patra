package com.patra.catalog.infra.adapter.integration.letpub;

import com.patra.catalog.domain.port.enrichment.LetPubVenueData;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

/// LetPub 期刊数据爬取客户端。
///
/// 通过 ISSN 在 [LetPub](https://www.letpub.com.cn) 搜索并爬取期刊评价数据，
/// 包括 JCR/CAS 分区、审稿速度、APC 费用等。
///
/// **两步爬取流程**：
///
/// 1. `searchByIssn(issn)` → GET 搜索页 → 提取 `journalid`
/// 2. `scrapeDetail(journalId)` → GET 详情页 → 委托 {@link LetPubDetailPageParser} 解析
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

  private final RestClient restClient;
  private final LetPubDetailPageParser detailPageParser;
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
    this.detailPageParser = new LetPubDetailPageParser();
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

  /// 解析详情页 HTML，委托给 {@link LetPubDetailPageParser}。
  ///
  /// @param html 详情页 HTML
  /// @param journalId LetPub 内部 ID
  /// @return 期刊评价数据
  LetPubVenueData parseDetailPage(String html, String journalId) {
    return detailPageParser.parse(html, journalId);
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
