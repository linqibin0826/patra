package dev.patra.gateway.config;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.ReactorClientHttpRequestFactoryBuilder;
import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcProperties;
import org.springframework.cloud.gateway.server.mvc.config.RouteProperties;
import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter.RequestHttpHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter.ResponseHttpHeadersFilter;
import org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctionDefinition;
import org.springframework.cloud.gateway.server.mvc.handler.ProxyExchangeHandlerFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

/// 推送路由的装配（PAP-145，设计 `docs/patra/specs/2026-10-31-notification-realtime-push-design.md` 第 7 节）。
///
/// 推送连接一借就是 30 分钟，和普通请求共用连接池会把普通请求挤到排队里。这里给推送单独一个池子：
/// 上限 `patra.gateway.push.max-connections`、不排队，满了新的推送立即失败（503），前端退回轮询。
///
/// 接法是 Gateway Server MVC 自带的扩展点：从配置建路由时，按 `uri` 的 scheme 找名为
/// `{scheme}HandlerFunctionDefinition` 的 Bean，自带的 `lb://` 就是这样接上负载均衡的。推送路由写
/// `lb-push://patra-notification`，负载均衡过滤器照用自带的，处理器换成用推送池的那一个。
///
/// 推送用的代理交换（[PushProxyExchange]，逐条事件刷出）与 `ProxyExchangeHandlerFunction` 都不注册成 Bean：
/// 自带的那两个是 `@ConditionalOnMissingBean`，多一个同类型的 Bean，所有 `lb://` 路由会悄悄换到推送池上。
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayPushProperties.class)
public class PushRouteConfiguration {

  /// 推送路由的 scheme。
  static final String SCHEME = "lb-push";

  /// 推送专用连接池的名字（出现在 Reactor Netty 的日志与指标里）。
  private static final String POOL_NAME = "patra-push";

  /// 推送路由的处理器：推送专用池 → Reactor Netty 客户端 → `RestClient` → 代理处理器。
  ///
  /// 客户端从 Boot 装好的 builder 派生，只换掉「从哪个池子建 `HttpClient`」：连接超时、读超时（空闲）、
  /// 不跟随重定向、不谈压缩、写超时都和普通路由一样（`spring.http.clients.*` 与 [GatewayConfiguration]）。
  ///
  /// @param requestFactoryBuilder Boot 按 `spring.http.clients.*` 装好、已应用定制器的 builder
  /// @param httpClientSettings Boot 绑定的连接超时、读超时、重定向策略
  /// @param restClientBuilder 容器里的 `RestClient.Builder`（单例、和自带的代理交换共用，只能 clone 后改）
  /// @param gatewayMvcProperties Gateway 的配置，代理交换要用
  /// @param requestHeadersFilters 出站请求头过滤器，容器刷新时由内部处理器收集
  /// @param responseHeadersFilters 响应头过滤器，同上
  /// @param push 推送池配置
  /// @return 推送路由的处理器
  @Bean
  public PushProxyHandlerFunction pushProxyHandlerFunction(
      ClientHttpRequestFactoryBuilder<?> requestFactoryBuilder,
      HttpClientSettings httpClientSettings,
      RestClient.Builder restClientBuilder,
      GatewayMvcProperties gatewayMvcProperties,
      ObjectProvider<RequestHttpHeadersFilter> requestHeadersFilters,
      ObjectProvider<ResponseHttpHeadersFilter> responseHeadersFilters,
      GatewayPushProperties push) {
    if (!(requestFactoryBuilder instanceof ReactorClientHttpRequestFactoryBuilder reactor)) {
      throw new IllegalStateException(
          "推送连接池要求 Reactor Netty 客户端（spring.http.clients.imperative.factory: REACTOR）");
    }
    ConnectionProvider pool =
        ConnectionProvider.builder(POOL_NAME)
            .maxConnections(push.maxConnections())
            .pendingAcquireMaxCount(0)
            .build();
    ClientHttpRequestFactory requestFactory =
        reactor.withHttpClientFactory(() -> HttpClient.create(pool)).build(httpClientSettings);
    // 先 clone：容器里的 RestClient.Builder 是 http-client 工具模块的单例（@Primary），自带的代理交换也用它；
    // 直接改它的 requestFactory，普通路由就跟着换到推送池上了
    RestClient restClient = restClientBuilder.clone().requestFactory(requestFactory).build();
    PushProxyExchange exchange = new PushProxyExchange(restClient, gatewayMvcProperties);
    ProxyExchangeHandlerFunction delegate =
        new ProxyExchangeHandlerFunction(exchange, requestHeadersFilters, responseHeadersFilters);
    return new PushProxyHandlerFunction(delegate, exchange, pool);
  }

  /// `lb-push://` 路由的处理器定义：负载均衡到 `uri` 的主机名（服务名），再用推送池转发。
  ///
  /// @param handler 推送路由的处理器
  /// @return 按路由配置给出处理器定义的函数
  @Bean(name = SCHEME + "HandlerFunctionDefinition")
  public Function<RouteProperties, HandlerFunctionDefinition> lbPushHandlerFunctionDefinition(
      PushProxyHandlerFunction handler) {
    return route -> {
      URI uri = Objects.requireNonNull(route.getUri(), "推送路由必须配置 uri");
      return new HandlerFunctionDefinition.Default(
          SCHEME, handler, List.of(), List.of(LoadBalancerFilterFunctions.lb(uri.getHost())));
    };
  }
}
