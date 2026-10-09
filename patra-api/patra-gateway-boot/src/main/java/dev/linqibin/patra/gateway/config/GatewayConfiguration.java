package dev.linqibin.patra.gateway.config;

import org.springframework.boot.http.client.JdkClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.autoconfigure.ClientHttpRequestFactoryBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// 网关自己的装配。错误映射在 `error` 包的 `GatewayProxyFailureAdvice` 里，这里只剩 HTTP 客户端的定制。
@Configuration(proxyBeanMethods = false)
public class GatewayConfiguration {

  /// 网关是代理，不替客户端谈压缩：JDK 客户端默认会在请求没带 `Accept-Encoding` 时补上 gzip，并把
  /// 下游的 gzip 响应解压、抹掉 `Content-Encoding` 和 `Content-Length`。关掉之后，客户端带什么
  /// `Accept-Encoding`、下游回什么 `Content-Encoding`，都原样经过。
  ///
  /// @return 作用在 Boot 按 `spring.http.clients.*` 装出的 JDK builder 上的定制
  @Bean
  public ClientHttpRequestFactoryBuilderCustomizer<JdkClientHttpRequestFactoryBuilder>
      gatewayHttpClientCustomizer() {
    return builder -> builder.withCustomizer(factory -> factory.enableCompression(false));
  }
}
