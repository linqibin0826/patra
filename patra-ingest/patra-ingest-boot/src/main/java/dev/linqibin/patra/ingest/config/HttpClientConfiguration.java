package dev.linqibin.patra.ingest.config;

import com.patra.objectstorage.api.endpoint.StorageEndpoint;
import com.patra.starter.httpinterface.factory.RestClientFactory;
import dev.linqibin.patra.registry.api.endpoint.DictionaryEndpoint;
import dev.linqibin.patra.registry.api.endpoint.ExprEndpoint;
import dev.linqibin.patra.registry.api.endpoint.ProvenanceEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/// HTTP Interface 客户端配置。
///
/// 注册 HTTP Interface 代理 Bean 用于跨服务调用：
///
/// - **Registry 服务**：ProvenanceEndpoint、ExprEndpoint、DictionaryEndpoint
/// - **Storage 服务**：StorageEndpoint
///
/// 使用 Spring Cloud LoadBalancer 进行服务发现和负载均衡。
///
/// **配置依赖**：
///
/// 需要在 `application.yml` 中配置服务分组：
///
/// ```yaml
/// patra:
///   http:
///     interface:
///       groups:
///         registry:
///           base-url: lb://patra-registry
///         storage:
///           base-url: lb://patra-object-storage
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Configuration
@ConditionalOnProperty(
    prefix = "patra.http.interface",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class HttpClientConfiguration {

  /// 创建 Registry 服务的 RestClient
  ///
  /// 配置负载均衡、服务发现、错误处理和跟踪传播，用于调用 patra-registry 服务。
  ///
  /// @param loadBalancedBuilder 负载均衡的 RestClient.Builder
  /// @param factory RestClient 工厂
  /// @return 配置好的 RestClient
  @Bean
  public RestClient registryRestClient(
      @Qualifier("httpInterfaceLoadBalancedRestClientBuilder")
          RestClient.Builder loadBalancedBuilder,
      RestClientFactory factory) {
    return factory.createRestClient(loadBalancedBuilder, "registry", "lb://patra-registry");
  }

  /// 创建 Storage 服务的 RestClient
  ///
  /// 配置负载均衡、服务发现、错误处理和跟踪传播，用于调用 patra-object-storage 服务。
  ///
  /// @param loadBalancedBuilder 负载均衡的 RestClient.Builder
  /// @param factory RestClient 工厂
  /// @return 配置好的 RestClient
  @Bean
  public RestClient storageRestClient(
      @Qualifier("httpInterfaceLoadBalancedRestClientBuilder")
          RestClient.Builder loadBalancedBuilder,
      RestClientFactory factory) {
    return factory.createRestClient(loadBalancedBuilder, "storage", "lb://patra-object-storage");
  }

  // ===== Registry 服务 HTTP Interface 代理 =====

  /// 创建 ProvenanceEndpoint HTTP Interface 代理
  ///
  /// @param registryRestClient Registry 服务 RestClient
  /// @param factory RestClient 工厂
  /// @return ProvenanceEndpoint 代理实例
  @Bean
  public ProvenanceEndpoint provenanceEndpoint(
      RestClient registryRestClient, RestClientFactory factory) {
    return factory.createProxy(registryRestClient, ProvenanceEndpoint.class);
  }

  /// 创建 ExprEndpoint HTTP Interface 代理
  ///
  /// @param registryRestClient Registry 服务 RestClient
  /// @param factory RestClient 工厂
  /// @return ExprEndpoint 代理实例
  @Bean
  public ExprEndpoint exprEndpoint(RestClient registryRestClient, RestClientFactory factory) {
    return factory.createProxy(registryRestClient, ExprEndpoint.class);
  }

  /// 创建 DictionaryEndpoint HTTP Interface 代理
  ///
  /// @param registryRestClient Registry 服务 RestClient
  /// @param factory RestClient 工厂
  /// @return DictionaryEndpoint 代理实例
  @Bean
  public DictionaryEndpoint dictionaryEndpoint(
      RestClient registryRestClient, RestClientFactory factory) {
    return factory.createProxy(registryRestClient, DictionaryEndpoint.class);
  }

  // ===== Storage 服务 HTTP Interface 代理 =====

  /// 创建 StorageEndpoint HTTP Interface 代理
  ///
  /// @param storageRestClient Storage 服务 RestClient
  /// @param factory RestClient 工厂
  /// @return StorageEndpoint 代理实例
  @Bean
  public StorageEndpoint storageEndpoint(RestClient storageRestClient, RestClientFactory factory) {
    return factory.createProxy(storageRestClient, StorageEndpoint.class);
  }
}
