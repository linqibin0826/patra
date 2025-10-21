package com.patra.starter.provenance.common.http;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import lombok.extern.slf4j.Slf4j;

/**
 * Tiny wrapper over Java 21 HttpClient with minimal retry/backoff support.
 *
 * <p>This intentionally avoids adding new Spring dependencies to the starter. It is adequate for
 * the current usage scope (PubMed/EPMC text/JSON calls). For binary/streaming use cases, prefer
 * direct storage presign flows.
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

    int maxRetries = rc != null && rc.maxRetries() != null ? Math.max(rc.maxRetries(), 0) : 0;
    long backoffSeconds =
        rc != null && rc.retryBackoffSeconds() != null ? rc.retryBackoffSeconds() : 0L;
    Duration timeout =
        rc != null && rc.timeoutSeconds() != null
            ? Duration.ofSeconds(Math.max(rc.timeoutSeconds(), 1L))
            : Duration.ofSeconds(30);

    int attempt = 0;
    while (true) {
      attempt++;
      try {
        if (rc != null && rc.rateLimitQps() != null && rc.rateLimitQps() > 0) {
          // Best-effort local throttle: sleep to approximate QPS (not distributed).
          long sleepMillis = 1000L / Math.max(1, rc.rateLimitQps());
          if (sleepMillis > 0) {
            try {
              Thread.sleep(sleepMillis);
            } catch (InterruptedException ignored) {
              Thread.currentThread().interrupt();
            }
          }
        }

        HttpRequest.Builder b = HttpRequest.newBuilder().uri(URI.create(url)).timeout(timeout);
        if (headers != null) headers.forEach(b::header);
        if ("GET".equals(method)) {
          b.GET();
        } else if ("POST".equals(method)) {
          b.POST(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
        } else {
          throw new IllegalArgumentException("Unsupported method: " + method);
        }

        long t0 = System.nanoTime();
        HttpResponse<String> resp = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
        long tookMs = (System.nanoTime() - t0) / 1_000_000;
        int status = resp.statusCode();
        log.debug("[HTTP] {} {} -> {} ({} ms)", method, url, status, tookMs);

        if (status >= 200 && status < 300) {
          return resp.body() == null ? "" : resp.body();
        }

        if (shouldRetry(status) && attempt <= maxRetries) {
          log.debug("[HTTP] retryable status={} attempt={}/{}", status, attempt, maxRetries);
          sleep(backoffSeconds);
          continue;
        }

        throw new IOException("HTTP status " + status + " body=" + truncate(resp.body()));
      } catch (IOException | InterruptedException ex) {
        if (attempt <= maxRetries) {
          log.debug(
              "[HTTP] exception, retrying attempt={}/{}: {}", attempt, maxRetries, ex.toString());
          sleep(backoffSeconds);
          continue;
        }
        if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
        throw new RuntimeException(
            "HTTP call failed after attempts=" + attempt + ": " + ex.getMessage(), ex);
      }
    }
  }

  private static Map<String, String> mergeHeaders(
      Map<String, String> base, Map<String, String> extra) {
    if (base == null || base.isEmpty()) return extra == null ? Map.of() : Map.copyOf(extra);
    if (extra == null || extra.isEmpty()) return Map.copyOf(base);
    java.util.LinkedHashMap<String, String> m = new java.util.LinkedHashMap<>(base);
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
}
