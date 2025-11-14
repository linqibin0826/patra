package com.patra.starter.provenance.common.http;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import lombok.extern.slf4j.Slf4j;

/**
 * Java 21 HttpClient 的轻量级封装,提供最小的重试/退避支持
 *
 * <p>此实现有意避免向 starter 添加新的 Spring 依赖。它足以满足当前使用范围(PubMed/EPMC 文本/JSON 调用)。 对于二进制/流式用例,建议使用直接存储预签名流程。
 *
 * <p><b>特性:</b>
 *
 * <ul>
 *   <li>支持 GET 和 POST(表单)请求
 *   <li>可配置的超时、重试和退避
 *   <li>尽力而为的速率限制(基于本地 sleep)
 *   <li>对可重试 HTTP 状态(408, 429, 5xx)自动重试
 *   <li>自动跟随重定向
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public class SimpleHttpClient {

  private final HttpClient client =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

  public String get(
      String baseUrl,
      String path,
      Map<String, String> queryParams,
      Map<String, String> headers,
      HttpResilienceConfig rc) {
    String url = buildUrl(baseUrl, path, queryParams);
    return execute("GET", url, null, headers, rc);
  }

  public String postForm(
      String baseUrl,
      String path,
      Map<String, String> formParams,
      Map<String, String> headers,
      HttpResilienceConfig rc) {
    String url = buildUrl(baseUrl, path, null);
    String body = encodeForm(formParams);
    headers = mergeHeaders(headers, Map.of("Content-Type", "application/x-www-form-urlencoded"));
    return execute("POST", url, body, headers, rc);
  }

  private String execute(
      String method,
      String url,
      String body,
      Map<String, String> headers,
      HttpResilienceConfig rc) {
    Objects.requireNonNull(url, "url");

    ResilienceParams params = extractResilienceParams(rc);
    int attempt = 0;

    while (true) {
      attempt++;
      try {
        applyRateLimit(params.rateLimitQps);
        HttpRequest request = buildHttpRequest(method, url, body, headers, params.timeout);
        HttpResponse<String> response = executeHttpRequest(request, method, url);

        String result =
            handleHttpResponse(response, attempt, params.maxRetries, params.backoffSeconds);
        if (result != null) {
          return result;
        }
      } catch (IOException | InterruptedException ex) {
        if (!handleHttpException(ex, attempt, params.maxRetries, params.backoffSeconds)) {
          handleExecutionFailure(ex, attempt);
        }
      }
    }
  }

  private String handleHttpResponse(
      HttpResponse<String> response, int attempt, int maxRetries, long backoffSeconds)
      throws IOException {
    if (isSuccessStatus(response.statusCode())) {
      return response.body() == null ? "" : response.body();
    }

    if (shouldRetry(response.statusCode()) && attempt <= maxRetries) {
      log.debug(
          "[HTTP] retryable status={} attempt={}/{}", response.statusCode(), attempt, maxRetries);
      sleep(backoffSeconds);
      return null;
    }

    throw new IOException(
        "HTTP status " + response.statusCode() + " body=" + truncate(response.body()));
  }

  private boolean handleHttpException(
      Exception ex, int attempt, int maxRetries, long backoffSeconds) {
    if (attempt <= maxRetries) {
      log.debug("[HTTP] exception, retrying attempt={}/{}: {}", attempt, maxRetries, ex);
      sleep(backoffSeconds);
      return true;
    }
    return false;
  }

  private ResilienceParams extractResilienceParams(HttpResilienceConfig rc) {
    int maxRetries = rc != null && rc.maxRetries() != null ? Math.max(rc.maxRetries(), 0) : 0;
    long backoffSeconds =
        rc != null && rc.retryBackoffSeconds() != null ? rc.retryBackoffSeconds() : 0L;
    Duration timeout =
        rc != null && rc.timeoutSeconds() != null
            ? Duration.ofSeconds(Math.max(rc.timeoutSeconds(), 1L))
            : Duration.ofSeconds(30);
    Integer rateLimitQps = rc != null ? rc.rateLimitQps() : null;

    return new ResilienceParams(maxRetries, backoffSeconds, timeout, rateLimitQps);
  }

  private void applyRateLimit(Integer rateLimitQps) {
    if (rateLimitQps == null || rateLimitQps <= 0) {
      return;
    }

    long sleepMillis = 1000L / Math.max(1, rateLimitQps);
    if (sleepMillis > 0) {
      try {
        Thread.sleep(sleepMillis);
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private HttpRequest buildHttpRequest(
      String method, String url, String body, Map<String, String> headers, Duration timeout) {
    HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url)).timeout(timeout);

    if (headers != null) {
      headers.forEach(builder::header);
    }

    if ("GET".equals(method)) {
      builder.GET();
    } else if ("POST".equals(method)) {
      builder.POST(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
    } else {
      throw new IllegalArgumentException("Unsupported method: " + method);
    }

    return builder.build();
  }

  private HttpResponse<String> executeHttpRequest(HttpRequest request, String method, String url)
      throws IOException, InterruptedException {
    long startTime = System.nanoTime();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    long durationMs = (System.nanoTime() - startTime) / 1_000_000;

    log.debug("[HTTP] {} {} -> {} ({} ms)", method, url, response.statusCode(), durationMs);
    return response;
  }

  private boolean isSuccessStatus(int status) {
    return status >= 200 && status < 300;
  }

  private void handleExecutionFailure(Exception ex, int attempt) {
    if (ex instanceof InterruptedException) {
      Thread.currentThread().interrupt();
    }
    throw new RuntimeException(
        "HTTP call failed after attempts=" + attempt + ": " + ex.getMessage(), ex);
  }

  private static Map<String, String> mergeHeaders(
      Map<String, String> base, Map<String, String> extra) {
    if (base == null || base.isEmpty()) return extra == null ? Map.of() : Map.copyOf(extra);
    if (extra == null || extra.isEmpty()) return Map.copyOf(base);
    LinkedHashMap<String, String> m = new LinkedHashMap<>(base);
    m.putAll(extra);
    return Map.copyOf(m);
  }

  private static boolean shouldRetry(int status) {
    return status == 408 || status == 429 || status == 503 || (status >= 500 && status < 600);
  }

  private static void sleep(long backoffSeconds) {
    if (backoffSeconds <= 0) return;
    try {
      Thread.sleep(backoffSeconds * 1000L);
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
  }

  private static String buildUrl(String baseUrl, String path, Map<String, String> queryParams) {
    StringBuilder sb = new StringBuilder();
    sb.append(trimTrailingSlash(baseUrl));
    if (path != null && !path.isBlank()) {
      if (!path.startsWith("/")) sb.append('/');
      sb.append(path);
    }
    if (queryParams != null && !queryParams.isEmpty()) {
      StringJoiner joiner = new StringJoiner("&");
      queryParams.forEach(
          (k, v) -> {
            if (k == null || v == null) return;
            joiner.add(encode(k) + "=" + encode(v));
          });
      sb.append("?").append(joiner.toString());
    }
    return sb.toString();
  }

  private static String encodeForm(Map<String, String> form) {
    if (form == null || form.isEmpty()) return "";
    StringJoiner joiner = new StringJoiner("&");
    form.forEach(
        (k, v) -> {
          if (k == null || v == null) return;
          joiner.add(encode(k) + "=" + encode(v));
        });
    return joiner.toString();
  }

  private static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }

  private static String trimTrailingSlash(String s) {
    if (s == null) return "";
    return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
  }

  private static String truncate(String s) {
    if (s == null) return "";
    return s.length() > 512 ? s.substring(0, 512) + "..." : s;
  }

  /** Holds resilience parameters extracted from configuration. */
  private record ResilienceParams(
      int maxRetries, long backoffSeconds, Duration timeout, Integer rateLimitQps) {}
}
