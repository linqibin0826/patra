package com.patra.starter.httpinterface.loadbalancer;

import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequestTransformer;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.support.HttpRequestWrapper;

/// 修复 lb:// scheme 的 LoadBalancer 请求转换器
///
/// **问题背景**：
///
/// Spring Cloud LoadBalancer 在使用 `lb://` 协议时，依赖 `ServiceInstance.getScheme()` 返回正确的
/// scheme（http 或 https）。但 Spring Cloud Consul 的 `ConsulServiceInstance` 继承自
/// `DefaultServiceInstance`，其 `getScheme()` 默认返回 null。
///
/// 当 `getScheme()` 返回 null 时，`LoadBalancerUriTools.reconstructURI()` 会使用原始 URI 的
/// scheme（即 "lb"），导致最终请求 URL 仍然是 `lb://host:port/path`，而 JDK HttpClient
/// 不认识 "lb" scheme，抛出 `IllegalArgumentException: invalid URI scheme lb`。
///
/// **解决方案**：
///
/// 此转换器在 LoadBalancer 选择服务实例后、请求发送前执行，检查 URI scheme：
///
/// - 如果 scheme 是 "lb"，则替换为 "http"（或根据 ServiceInstance.isSecure() 选择 "https"）
/// - 如果 scheme 已经是 "http" 或 "https"，则不做修改
///
/// **参考**：
///
/// - [GitHub Issue #1320](https://github.com/spring-cloud/spring-cloud-commons/issues/1320)
/// - [GitHub Issue #319](https://github.com/spring-cloud/spring-cloud-consul/issues/319)
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class LbSchemeFixingRequestTransformer implements LoadBalancerRequestTransformer {

  /// 转换请求，修复 lb:// scheme
  ///
  /// @param request 原始请求（可能包含 lb:// scheme）
  /// @param instance 选中的服务实例
  /// @return 修复后的请求（使用 http:// 或 https:// scheme）
  @Override
  public HttpRequest transformRequest(HttpRequest request, ServiceInstance instance) {
    URI originalUri = request.getURI();
    String scheme = originalUri.getScheme();

    // 如果 scheme 已经是 http/https，不需要修复
    if ("http".equals(scheme) || "https".equals(scheme)) {
      return request;
    }

    // 如果 scheme 是 lb，替换为正确的 http/https
    if ("lb".equals(scheme)) {
      String targetScheme = instance.isSecure() ? "https" : "http";
      URI fixedUri = replaceScheme(originalUri, targetScheme);

      log.debug(
          "修复 lb:// scheme: {} -> {} (服务实例: {}:{})",
          originalUri,
          fixedUri,
          instance.getHost(),
          instance.getPort());

      return new SchemeFixedHttpRequest(request, fixedUri);
    }

    // 其他 scheme（如 ws://），保持不变
    return request;
  }

  /// 替换 URI 的 scheme
  private URI replaceScheme(URI original, String newScheme) {
    try {
      return new URI(
          newScheme,
          original.getUserInfo(),
          original.getHost(),
          original.getPort(),
          original.getPath(),
          original.getQuery(),
          original.getFragment());
    } catch (Exception e) {
      log.warn("替换 URI scheme 失败: {} -> {}, 错误: {}", original, newScheme, e.getMessage());
      return original;
    }
  }

  /// 包装 HttpRequest，返回修复后的 URI
  private static class SchemeFixedHttpRequest extends HttpRequestWrapper {

    private final URI fixedUri;

    SchemeFixedHttpRequest(HttpRequest request, URI fixedUri) {
      super(request);
      this.fixedUri = fixedUri;
    }

    @Override
    public URI getURI() {
      return fixedUri;
    }
  }
}
