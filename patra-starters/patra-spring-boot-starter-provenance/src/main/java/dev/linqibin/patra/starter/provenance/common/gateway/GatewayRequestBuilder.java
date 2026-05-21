package dev.linqibin.patra.starter.provenance.common.gateway;

import dev.linqibin.patra.starter.provenance.common.config.ProvenanceConfig;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringJoiner;

/// 网关请求构建器
///
/// 用于在烟雾测试/集成测试中构建完整的请求URL，无需依赖网关DTO对象。 主要提供URL拼接、查询参数编码等基础功能。
///
/// @author linqibin
/// @since 0.1.0
public class GatewayRequestBuilder {

  /// 构建后的请求记录，测试用简单DTO
  ///
  /// @param url 完整的请求URL（包含查询参数）
  public record BuiltRequest(String url) {}

  /// 构建完整的请求 URL。
  ///
  /// @param baseUrl 基础 URL
  /// @param path API 路径
  /// @param req 请求参数对象
  /// @param cfg 数据源配置
  /// @return 构建后的请求对象
  public BuiltRequest build(String baseUrl, String path, ApiRequest req, ProvenanceConfig cfg) {
    String url = join(baseUrl, path, req != null ? req.toQueryParams() : Map.of());
    return new BuiltRequest(url);
  }

  /// 拼接 URL 和查询参数。
  ///
  /// @param baseUrl 基础 URL
  /// @param path API 路径
  /// @param query 查询参数映射
  /// @return 完整的 URL 字符串
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

  /// 去除 URL 尾部的斜杠。
  ///
  /// @param s URL 字符串
  /// @return 去除尾部斜杠后的 URL
  private static String trimTrailingSlash(String s) {
    if (s == null) return "";
    return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
  }

  /// 对字符串进行 URL 编码。
  ///
  /// @param s 待编码的字符串
  /// @return URL 编码后的字符串
  private static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
