package dev.patra.gateway.config;

import io.netty.handler.timeout.WriteTimeoutHandler;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.http.client.ReactorClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.autoconfigure.ClientHttpRequestFactoryBuilderCustomizer;
import org.springframework.boot.http.client.autoconfigure.HttpClientsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// 网关自己的装配。错误映射在 `error` 包的 `GatewayProxyFailureAdvice` 里，这里只剩 HTTP 客户端的定制。
@Configuration(proxyBeanMethods = false)
public class GatewayConfiguration {

  /// 写超时处理器在连接 pipeline 里的名字；同名只加一次。
  private static final String WRITE_TIMEOUT_HANDLER = "patra.gateway.writeTimeout";

  /// 定制 Boot 按 `spring.http.clients.*` 装出的 Reactor Netty 客户端，两件事：
  ///
  /// - **不谈压缩**：Boot 默认开了压缩，会在请求没带 `Accept-Encoding` 时补上 gzip，并把下游的
  ///   gzip 响应解压、抹掉 `Content-Encoding`。关掉之后，客户端带什么 `Accept-Encoding`、下游回什么
  ///   `Content-Encoding`，都原样经过。
  /// - **写也有上限**：`read-timeout` 映射到的 `responseTimeout` 在请求发完之后才开始计时，下游不读
  ///   请求体时网关的写会一直卡着。每个请求挂一个 Netty 的 `WriteTimeoutHandler`，单次写停住超过
  ///   读超时就断开（504），和读一样按空闲计时，慢而持续的上传不受影响。挂在请求上而不是连接上：
  ///   Reactor Netty 在连接归还连接池时移除这类处理器，复用的连接上要每次重新挂。
  ///
  /// @param httpClients Boot 绑定的 `spring.http.clients.*`；读写共用其中的 `read-timeout`
  /// @return 作用在 Reactor builder 上的定制
  @Bean
  public ClientHttpRequestFactoryBuilderCustomizer<ReactorClientHttpRequestFactoryBuilder>
      gatewayHttpClientCustomizer(HttpClientsProperties httpClients) {
    Duration idleTimeout =
        Objects.requireNonNull(
            httpClients.getReadTimeout(), "spring.http.clients.read-timeout 必须配置");
    return builder ->
        builder.withHttpClientCustomizer(
            httpClient ->
                httpClient
                    .compress(false)
                    .doOnRequest(
                        (request, connection) ->
                            connection.addHandlerLast(
                                WRITE_TIMEOUT_HANDLER,
                                new WriteTimeoutHandler(
                                    idleTimeout.toMillis(), TimeUnit.MILLISECONDS))));
  }
}
