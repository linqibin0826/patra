package dev.linqibin.patra.catalog.infra.adapter.integration.letpub;

import cn.hutool.core.exceptions.ExceptionUtil;
import dev.linqibin.patra.catalog.domain.port.enrichment.LetPubVenueData;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

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

  /// 搜索请求的 Referer（模拟用户从期刊查询首页进入搜索）。
  private static final String SEARCH_REFERER =
      "https://www.letpub.com.cn/index.php?page=journalapp";

  /// ISSN 搜索 URL 的公共查询串尾部。
  ///
  /// `fetchSearchPage` 的 URI 模板和 `buildDetailReferer` 的 Referer 都要以
  /// **逐字节相同**的查询串结尾，以匹配真实浏览器表单提交的格式。抽到常量可确保两处同步。
  private static final String SEARCH_QUERY_TRAILER =
      "&searchfield=&searchimpactlow=&searchimpacthigh="
          + "&searchsci498telecomkey=&searchcategory1=&searchcategory2="
          + "&searchjcrkind=&searchopenaccess=&searchsort=relevance";

  /// 日志中响应体最大字符数（避免单条日志过长）。
  private static final int LOG_BODY_MAX_CHARS = 300;

  /// 瞬时异常重试退避序列（ms）。长度隐式决定最大尝试次数 = length + 1。
  private static final long[] PROXY_RETRY_BACKOFF_MS = {2_000L, 5_000L};

  /// 软限流退避序列（ms）。长度决定 `MAX_RATE_LIMIT_RETRIES`。
  private static final long[] RATE_LIMIT_BACKOFF_MS = {30_000L, 60_000L, 120_000L, 240_000L};

  /// 限流检测阈值：响应体小于此字节数时检查是否被限流。
  ///
  /// 阻挡页通常是轻量 HTML（< 1500 字节），正常详情页 >= 400 KB。
  /// 该阈值双重保险：即使阻挡关键词偶然出现在正常页的文案里，也不会误判。
  static final int RATE_LIMIT_THRESHOLD = 1500;

  /// LetPub 阻挡页关键词集合（任一命中即判定为阻挡响应）。
  ///
  /// **识别的三种阻挡模式**：
  ///
  /// 1. `速度过快`：显式"请求过快"限流页
  /// 2. `您使用的IP地址`：IP 封锁页（参考自社区实现 kakarotto-yang/letpub）
  /// 3. `注册或登录后，查看影响因子和历年趋势图`：软降级页（游客访问触发阈值后返回的"仅登录用户可见"占位页）
  ///
  /// 这三种文案**不会**出现在正常详情页里（已通过 curl 实际详情页 HTML 验证），
  /// 配合 `RATE_LIMIT_THRESHOLD` 长度兜底，假阳性风险为 0。
  static final List<String> BLOCK_KEYWORDS = List.of("速度过快", "您使用的IP地址", "注册或登录后，查看影响因子和历年趋势图");

  /// 最大限流重试次数。派生自 {@link #RATE_LIMIT_BACKOFF_MS} 长度，确保两者永不失步。
  static final int MAX_RATE_LIMIT_RETRIES = RATE_LIMIT_BACKOFF_MS.length;

  /// journalid 提取正则（排除 journalid=0）。
  private static final Pattern JOURNAL_ID_PATTERN =
      Pattern.compile("journalid=(\\d+).*?view=detail");

  /// User-Agent 轮换池。
  ///
  /// 每次请求随机挑选一个，避免固定 UA 成为反爬指纹。包含 Chrome/Firefox/Safari/Edge
  /// 在 Windows 与 macOS 上的最新版本，覆盖真实用户常见组合。
  private static final List<String> USER_AGENT_POOL =
      List.of(
          "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
              + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
          "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
              + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
          "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
              + "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
          "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 "
              + "(KHTML, like Gecko) Version/17.5 Safari/605.1.15",
          "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0",
          "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:131.0) Gecko/20100101 Firefox/131.0",
          "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
              + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0");

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
  /// **异常分类**（从日志 `[CATEGORY]` 标签可快速定位故障点）：
  ///
  /// - `UPSTREAM_HTTP_{status}`：LetPub 返回了非 2xx 状态码（目标站点问题）
  /// - `UPSTREAM_TRUNCATED`：LetPub 主动截断响应流（隐蔽反爬：chunk 缺斤短两）
  /// - `PROXY_TUNNEL_REFUSED`：代理拒绝建立 HTTPS 隧道（代理配额/并发超限）
  /// - `PROXY_UNREACHABLE`：连不上代理服务器本身
  /// - `CONNECT_TIMEOUT` / `SOCKET_TIMEOUT`：连接或读取阶段超时
  /// - `DNS_FAILED`：域名解析失败
  /// - `NETWORK_IO`：其他未分类的网络 I/O 错误
  /// - `UNKNOWN`：非网络异常（通常是 HTML 解析错误等）
  ///
  /// @param issn ISSN 标识符
  /// @return 期刊评价数据，未找到返回 empty
  public Optional<LetPubVenueData> findByIssn(String issn) {
    if (issn == null || issn.isBlank()) {
      return Optional.empty();
    }

    try {
      delay();
      String searchHtml = fetchWithProxyRetry("search:" + issn, () -> fetchSearchPage(issn));
      if (isRateLimited(searchHtml)) {
        logRateLimitBody("search", issn, searchHtml);
        searchHtml = retryWithBackoff(() -> fetchSearchPage(issn));
      }

      Optional<String> journalIdOpt = parseSearchResult(searchHtml);
      if (journalIdOpt.isEmpty()) {
        log.debug("LetPub 未找到 ISSN {} 对应的期刊", issn);
        return Optional.empty();
      }
      String jid = journalIdOpt.get();

      delay();
      String detailHtml = fetchWithProxyRetry("detail:" + jid, () -> fetchDetailPage(jid, issn));
      if (isRateLimited(detailHtml)) {
        logRateLimitBody("detail", issn, detailHtml);
        detailHtml = retryWithBackoff(() -> fetchDetailPage(jid, issn));
      }

      LetPubVenueData data = parseDetailPage(detailHtml, jid);
      log.info(
          "LetPub 成功爬取 ISSN {} → journalId={}, name={}", issn, jid, data.basicInfo().letPubName());
      return Optional.of(data);

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("LetPub 爬取被中断: ISSN={}", issn);
      return Optional.empty();
    } catch (RestClientResponseException e) {
      // LetPub 自身返回了 4xx/5xx HTTP 状态码（与代理无关）
      String tag = "UPSTREAM_HTTP_" + e.getStatusCode().value();
      log.warn(
          "LetPub 爬取失败 [{}]: ISSN={}, body={}",
          tag,
          issn,
          truncateBody(e.getResponseBodyAsString()));
      throw new LetPubScrapingException("LetPub 爬取失败 [" + tag + "]: ISSN=" + issn, e);
    } catch (RestClientException e) {
      // 覆盖所有 RestClient 级异常：
      // - ResourceAccessException：底层 IOException（代理/连接/超时/DNS）
      // - 裸 RestClientException：响应体读取异常（如 TruncatedChunkException、StringHttpMessageConverter
      // IOException）
      // 两者本质都是网络/代理层瞬时异常，用同一个分类器 classifyIoError 处理
      LetPubErrorCategory category = classifyIoError(e);
      Throwable root = ExceptionUtil.getRootCause(e);
      log.warn(
          "LetPub 爬取失败 [{}]: ISSN={}, rootCause={}: {}",
          category,
          issn,
          root.getClass().getSimpleName(),
          root.getMessage());
      if (log.isDebugEnabled()) {
        log.debug("LetPub 爬取 I/O 异常完整堆栈: ISSN=" + issn, e);
      }
      throw new LetPubScrapingException("LetPub 爬取失败 [" + category + "]: ISSN=" + issn, e);
    } catch (Exception e) {
      // 未分类异常（通常是 HTML 解析失败等）
      log.warn("LetPub 爬取失败 [UNKNOWN]: ISSN={}, error={}", issn, e.getMessage(), e);
      throw new LetPubScrapingException("LetPub 爬取失败 [UNKNOWN]: ISSN=" + issn, e);
    }
  }

  // ========== HTTP 调用 ==========

  /// 获取搜索结果页面。
  ///
  /// 每次请求带 {@link #antiBotHeaders} 构造的反爬伪装头部（随机 UA + `Connection: close` + `Referer`）。
  String fetchSearchPage(String issn) {
    return restClient
        .get()
        .uri(
            SEARCH_URL
                + "?page=journalapp&view=search&searchname=&searchissn={issn}"
                + SEARCH_QUERY_TRAILER,
            issn)
        .headers(antiBotHeaders(SEARCH_REFERER))
        .retrieve()
        .body(String.class);
  }

  /// 获取详情页面。
  ///
  /// 与 {@link #fetchSearchPage} 同样应用 {@link #antiBotHeaders}，但 Referer
  /// 构造成"用户从 ISSN 搜索结果点进详情页"的轨迹 URL，比通用首页 Referer 更像真实行为。
  ///
  /// @param journalId LetPub 内部期刊 ID
  /// @param issn 发起本次爬取的 ISSN，用于构造"从搜索结果跳转"的 Referer
  String fetchDetailPage(String journalId, String issn) {
    return restClient
        .get()
        .uri(SEARCH_URL + "?journalid={journalId}&page=journalapp&view=detail", journalId)
        .headers(antiBotHeaders(buildDetailReferer(issn)))
        .retrieve()
        .body(String.class);
  }

  /// 构造反爬伪装头部设置器。
  ///
  /// - 随机 User-Agent：避免固定指纹被反爬识别
  /// - `Connection: close`：配合 `HttpClientBuilder.setConnectionReuseStrategy(false)`，
  ///   确保下次请求重新走代理 CONNECT 获取新上游 IP
  /// - `Referer`：模拟真实用户点击轨迹（缺失 Referer 是典型爬虫签名）
  ///
  /// @param referer 要设置的 Referer URL
  /// @return `RestClient.headers()` 接受的头部设置器
  private Consumer<HttpHeaders> antiBotHeaders(String referer) {
    return headers -> {
      headers.set("User-Agent", pickRandomUserAgent());
      headers.set("Connection", "close");
      headers.set("Referer", referer);
    };
  }

  /// 构造详情页请求的 Referer URL，模拟"用户从 ISSN 搜索结果点击进入详情"的轨迹。
  private String buildDetailReferer(String issn) {
    return SEARCH_URL
        + "?page=journalapp&view=search&searchname=&searchissn="
        + issn
        + SEARCH_QUERY_TRAILER;
  }

  /// 从 UA 池中随机挑选一个 User-Agent。
  ///
  /// 使用 {@link ThreadLocalRandom} 避免多线程下 `Math.random()` 的竞争。
  String pickRandomUserAgent() {
    return USER_AGENT_POOL.get(ThreadLocalRandom.current().nextInt(USER_AGENT_POOL.size()));
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

  /// 判断响应是否为阻挡/限流页面。
  ///
  /// **判定规则**：响应体 < {@link #RATE_LIMIT_THRESHOLD} 字节 **且** 命中任一 {@link #BLOCK_KEYWORDS} 关键词。
  /// 长度阈值是重要的假阳性防护——正常详情页 ~480 KB，即便文案里偶然包含关键词也不会被误判。
  ///
  /// @param body 响应体
  /// @return true 表示被阻挡
  boolean isRateLimited(String body) {
    if (body == null || body.isEmpty()) {
      return false;
    }
    if (body.length() >= RATE_LIMIT_THRESHOLD) {
      return false;
    }
    for (String keyword : BLOCK_KEYWORDS) {
      if (body.contains(keyword)) {
        return true;
      }
    }
    return false;
  }

  /// 记录软限流响应体内容，便于诊断 LetPub 的限流策略。
  ///
  /// 由于 {@link #isRateLimited} 的长度阈值是 {@link #RATE_LIMIT_THRESHOLD} 字节，
  /// 限流页面整体很小，这里直接折叠空白后整体输出，便于在日志里直接看到
  /// LetPub 返回的真实文案（包括等待时间、锁定时长等可能的线索）。
  ///
  /// @param stage 请求阶段（search / detail）
  /// @param issn ISSN 标识符
  /// @param body 限流响应体
  private void logRateLimitBody(String stage, String issn, String body) {
    String normalized = body.replaceAll("\\s+", " ").trim();
    log.info("LetPub 软限流触发 [{}] ISSN={}, 响应体: {}", stage, issn, normalized);
  }

  // ========== 反爬/重试 ==========

  /// 等待基础延迟 + 随机抖动。
  private void delay() throws InterruptedException {
    if (baseDelayMs > 0) {
      long jitter = jitterMs > 0 ? ThreadLocalRandom.current().nextLong(jitterMs) : 0L;
      Thread.sleep(baseDelayMs + jitter);
    }
  }

  /// 限流时指数退避重试。退避序列来自 {@link #RATE_LIMIT_BACKOFF_MS}。
  private String retryWithBackoff(ThrowingSupplier<String> fetcher) throws Exception {
    for (int i = 0; i < RATE_LIMIT_BACKOFF_MS.length; i++) {
      log.warn("LetPub 限流检测，第 {} 次退避等待 {}s", i + 1, RATE_LIMIT_BACKOFF_MS[i] / 1000);
      Thread.sleep(RATE_LIMIT_BACKOFF_MS[i]);
      String result = fetcher.get();
      if (!isRateLimited(result)) {
        return result;
      }
    }
    throw new LetPubScrapingException("LetPub 限流重试超过最大次数: " + MAX_RATE_LIMIT_RETRIES);
  }

  /// 执行 HTTP 请求，遇到瞬时网络/代理异常时自动重试。
  ///
  /// 可/不可重试的判定由 {@link LetPubErrorCategory#isRetryable()} 决定，
  /// 退避序列来自 {@link #PROXY_RETRY_BACKOFF_MS}（默认 2s → 5s，共最多 3 次尝试）。
  /// 配合禁用 Keep-Alive，每次重试都走新 TCP 连接 + 新代理上游 IP。
  ///
  /// @param label 用于日志的请求标签（如 `search:0003-2700`）
  /// @param action HTTP 调用动作
  /// @return HTTP 响应体
  /// @throws Exception 若非可重试错误或重试仍失败
  String fetchWithProxyRetry(String label, ThrowingSupplier<String> action) throws Exception {
    int maxAttempts = PROXY_RETRY_BACKOFF_MS.length + 1;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        return action.get();
      } catch (RestClientException e) {
        LetPubErrorCategory category = classifyIoError(e);
        if (!category.isRetryable() || attempt == maxAttempts) {
          throw e;
        }
        long backoff = PROXY_RETRY_BACKOFF_MS[attempt - 1];
        log.info(
            "LetPub 瞬时异常 [{}]（{}，第 {}/{} 次尝试），退避 {}ms 后重试",
            category,
            label,
            attempt,
            maxAttempts,
            backoff);
        Thread.sleep(backoff);
      }
    }
    throw new IllegalStateException("fetchWithProxyRetry 不可达分支");
  }

  // ========== 异常分类 ==========

  /// 对 I/O 异常进行分类，帮助从日志快速定位故障点。
  ///
  /// 遍历异常链，基于异常类名和消息关键字匹配。注：Apache HttpClient 5 的异常类
  /// （如 `TruncatedChunkException`）未直接 import，采用类名包含匹配，避免 infra 层
  /// 硬依赖 starter-rest-client 的内部实现细节。
  ///
  /// @param e 异常（通常是 `RestClientException` 及其 cause 链）
  /// @return 分类枚举，兜底为 {@link LetPubErrorCategory#NETWORK_IO}
  LetPubErrorCategory classifyIoError(Throwable e) {
    Throwable current = e;
    while (current != null) {
      String name = current.getClass().getSimpleName();
      String msg = current.getMessage() != null ? current.getMessage() : "";

      if (name.contains("TunnelRefused")
          || msg.contains("Tunnel failed")
          || msg.contains("Tunnel refused")
          || msg.contains("CONNECT refused")) {
        return LetPubErrorCategory.PROXY_TUNNEL_REFUSED;
      }
      if (name.contains("TruncatedChunk") || msg.contains("Truncated chunk")) {
        return LetPubErrorCategory.UPSTREAM_TRUNCATED;
      }
      if (current instanceof SocketTimeoutException) {
        return msg.toLowerCase(Locale.ROOT).contains("connect")
            ? LetPubErrorCategory.CONNECT_TIMEOUT
            : LetPubErrorCategory.SOCKET_TIMEOUT;
      }
      if (current instanceof UnknownHostException) {
        return LetPubErrorCategory.DNS_FAILED;
      }
      if (current instanceof ConnectException) {
        return LetPubErrorCategory.PROXY_UNREACHABLE;
      }
      current = current.getCause();
    }
    return LetPubErrorCategory.NETWORK_IO;
  }

  /// 截断响应体以避免日志过长（最多 {@link #LOG_BODY_MAX_CHARS} 字符）。
  private String truncateBody(String body) {
    if (body == null || body.isEmpty()) {
      return "";
    }
    return body.length() <= LOG_BODY_MAX_CHARS
        ? body
        : body.substring(0, LOG_BODY_MAX_CHARS) + "...(truncated)";
  }

  /// LetPub 爬取的 I/O 异常分类。
  ///
  /// `retryable` 属性直接绑定到每个枚举值，消除了字符串 switch 的心智负担和拼写风险。
  /// **选择重试与否的依据**：
  ///
  /// - 瞬时/可恢复：代理隧道抖动、上游截断、握手/读取超时、其他瞬时 I/O
  /// - 不可恢复：DNS 解析失败、代理服务器不可达（基础设施问题）
  enum LetPubErrorCategory {
    /// 代理 CONNECT 被拒（瞬时配额/并发抖动）。
    PROXY_TUNNEL_REFUSED(true),
    /// LetPub 主动截断 chunked 响应（隐蔽反爬）。
    UPSTREAM_TRUNCATED(true),
    /// 握手阶段超时（快速失败，可低成本重试）。
    CONNECT_TIMEOUT(true),
    /// 读响应阶段超时（LetPub 挂连接式隐式限流的典型表现，换 IP 大概率恢复）。
    SOCKET_TIMEOUT(true),
    /// 其他未分类瞬时 I/O 异常。
    NETWORK_IO(true),
    /// 域名无法解析，重试不会恢复。
    DNS_FAILED(false),
    /// 代理服务器连不上，基础设施级问题。
    PROXY_UNREACHABLE(false);

    private final boolean retryable;

    LetPubErrorCategory(boolean retryable) {
      this.retryable = retryable;
    }

    /// @return true 表示此类异常应当重试
    boolean isRetryable() {
      return retryable;
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
