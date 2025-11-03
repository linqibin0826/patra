package com.patra.starter.provenance.common.gateway;

import com.patra.starter.provenance.common.config.ProvenanceConfig;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 网关请求构建器
 *
 * <p>用于在烟雾测试/集成测试中构建完整的请求URL，无需依赖网关DTO对象。 主要提供URL拼接、查询参数编码等基础功能。
 *
 * @author linqibin
 * @since 0.1.0
 */
public class GatewayRequestBuilder {

  /** 构建后的请求记录，测试用简单DTO */
  public record BuiltRequest(String url) {}

  public BuiltRequest build(String baseUrl, String path, ApiRequest req, ProvenanceConfig cfg) {
    String url = join(baseUrl, path, req != null ? req.toQueryParams() : Map.of());
    return new BuiltRequest(url);
  }

  private static String join(String baseUrl, String path, Map<String, String> query) {
    StringBuilder sb = new StringBuilder();
    sb.append(trimTrailingSlash(baseUrl));
    if (path != null && !path.isBlank()) {
      if (!path.startsWith("/")) sb.append('/');
      sb.append(path);
    }
    if (query != null && !query.isEmpty()) {
      StringJoiner joiner = new StringJoiner("&");
      query.forEach(
          (k, v) -> {
            if (k == null || v == null) return;
            joiner.add(encode(k) + "=" + encode(v));
          });
      sb.append('?').append(joiner.toString());
    }
    return sb.toString();
  }

  private static String trimTrailingSlash(String s) {
    if (s == null) return "";
    return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
  }

  private static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
