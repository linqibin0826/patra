package com.patra.starter.restclient.config;

import io.netty.channel.ChannelOption;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/// 流式下载 WebClient 自动配置类。
///
/// 提供专门用于流式下载的 WebClient Bean，解决 RestClient.exchange() 在长时间流式传输过程中
/// 意外关闭 InputStream 的问题。
///
/// ## 问题背景
///
/// RestClient.exchange(close=false) 在流式下载大文件时，底层连接可能被意外关闭，
/// 导致 `IOException: closed` 异常。经验证，JDK HttpClient 直接使用正常，
/// 问题出在 Spring RestClient 层。
///
/// ## 解决方案
///
/// 使用 WebClient + Reactor Netty 作为流式下载的替代方案：
/// - WebClient 原生支持 Reactive Streams，背压处理更可靠
/// - Reactor Netty 是成熟的非阻塞 HTTP 客户端
/// - 可以通过 `bodyToFlux(DataBuffer.class)` 获取流式数据
///
/// ## 配置说明
///
/// - 默认启用：`patra.rest-client.streaming.enabled=true`
/// - 连接超时：30 秒
/// - 响应超时：10 分钟（与 longRunningRestClient 一致，适合大文件下载）
/// - 内存限制：-1（不限制，使用流式处理，不缓存到内存）
///
/// ## 使用方式
///
/// ```java
/// @Autowired
/// @Qualifier("streamingWebClient")
/// private WebClient webClient;
///
/// Flux<DataBuffer> flux = webClient.get()
///     .uri(url)
///     .retrieve()
///     .bodyToFlux(DataBuffer.class);
/// ```
///
/// @author linqibin
/// @since 0.1.0
/// @see RestClientAutoConfiguration
@Slf4j
@AutoConfiguration
@ConditionalOnClass(WebClient.class)
@EnableConfigurationProperties(RestClientProperties.class)
@ConditionalOnProperty(
    prefix = "patra.rest-client.streaming",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class StreamingWebClientAutoConfiguration {

  /// 默认连接超时（30 秒）。
  private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(30);

  /// 默认响应超时（10 分钟，与 longRunningRestClient 一致）。
  private static final Duration DEFAULT_RESPONSE_TIMEOUT = Duration.ofMinutes(10);

  /// 创建流式下载专用的 WebClient。
  ///
  /// 配置：
  /// - 连接超时：30 秒
  /// - 响应超时：10 分钟
  /// - 内存限制：-1（不限制，使用流式处理）
  ///
  /// @param properties REST 客户端配置属性
  /// @return 配置完成的 WebClient
  @Bean
  @ConditionalOnMissingBean(name = "streamingWebClient")
  public WebClient streamingWebClient(RestClientProperties properties) {
    Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;
    Duration responseTimeout = DEFAULT_RESPONSE_TIMEOUT;

    // 如果配置了 long-running 客户端的超时，使用相同配置
    var longRunningConfig = properties.getClients().get("long-running");
    if (longRunningConfig != null && longRunningConfig.getTimeout() != null) {
      var timeout = longRunningConfig.getTimeout();
      if (timeout.connect() != null) {
        connectTimeout = timeout.connect();
      }
      if (timeout.read() != null) {
        responseTimeout = timeout.read();
      }
    }

    log.info(
        "创建 streamingWebClient，connectTimeout={}s，responseTimeout={}s",
        connectTimeout.toSeconds(),
        responseTimeout.toSeconds());

    // 创建 Reactor Netty HttpClient
    HttpClient httpClient =
        HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis())
            .responseTimeout(responseTimeout);

    return WebClient.builder()
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(-1)) // 不缓存到内存，使用流式处理
        .build();
  }
}
