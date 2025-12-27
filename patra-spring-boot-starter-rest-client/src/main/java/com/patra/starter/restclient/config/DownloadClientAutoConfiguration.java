package com.patra.starter.restclient.config;

import com.patra.starter.restclient.download.CompositeProgressListener;
import com.patra.starter.restclient.download.DefaultDownloadClient;
import com.patra.starter.restclient.download.DownloadClient;
import com.patra.starter.restclient.download.LoggingProgressListener;
import com.patra.starter.restclient.download.MetricsProgressListener;
import com.patra.starter.restclient.download.ProgressListener;
import com.patra.starter.restclient.download.strategy.FtpStreamingDownloader;
import com.patra.starter.restclient.download.strategy.HttpStreamingDownloader;
import com.patra.starter.restclient.download.strategy.StreamingDownloader;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// 下载客户端自动配置。
///
/// 自动配置统一下载客户端和默认监听器。
///
/// **配置的 Bean**：
/// - `downloadClient`：统一下载客户端（流式 + 落盘）
/// - `defaultProgressListener`：组合日志和指标监听器
///
/// **依赖条件**：
/// - WebClient 可用时自动启用 HTTP/HTTPS 流式下载
/// - FTP 下载默认启用，可通过配置关闭
/// - 如果存在 MeterRegistry，会自动添加 MetricsProgressListener
///
/// @author linqibin
/// @since 0.1.0
@AutoConfiguration(
    after = {RestClientAutoConfiguration.class, StreamingWebClientAutoConfiguration.class})
@ConditionalOnProperty(
    prefix = "patra.rest-client.download",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(DownloadProperties.class)
public class DownloadClientAutoConfiguration {

  /// 默认日志输出间隔（每 10% 输出一次）
  private static final int DEFAULT_LOG_INTERVAL_PERCENT = 10;

  /// 创建下载客户端。
  ///
  /// @param properties 下载配置
  /// @param downloaders 流式下载策略列表
  /// @return 下载客户端
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(StreamingDownloader.class)
  public DownloadClient downloadClient(
      DownloadProperties properties, List<StreamingDownloader> downloaders) {
    return new DefaultDownloadClient(downloaders, properties);
  }

  /// 创建默认进度监听器。
  ///
  /// 组合日志监听器和指标监听器（如果 MeterRegistry 可用）。
  ///
  /// @param registry Micrometer 指标注册中心（可选）
  /// @return 组合进度监听器
  @Bean
  @ConditionalOnMissingBean
  public ProgressListener defaultProgressListener(Optional<MeterRegistry> registry) {
    var listeners = new ArrayList<ProgressListener>();

    // 始终添加日志监听器
    listeners.add(new LoggingProgressListener(DEFAULT_LOG_INTERVAL_PERCENT));

    // 如果有 MeterRegistry，添加指标监听器
    registry.ifPresent(r -> listeners.add(new MetricsProgressListener(r)));

    return CompositeProgressListener.of(listeners);
  }

  /// FTP 流式下载策略。
  ///
  /// @param properties 下载配置
  /// @return FTP 流式下载策略
  @Bean
  @ConditionalOnMissingBean(FtpStreamingDownloader.class)
  @ConditionalOnProperty(
      prefix = "patra.rest-client.download.ftp",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public StreamingDownloader ftpStreamingDownloader(DownloadProperties properties) {
    return new FtpStreamingDownloader(properties);
  }

  /// HTTP/HTTPS 流式下载策略配置。
  @Configuration
  @ConditionalOnClass(name = "org.springframework.web.reactive.function.client.WebClient")
  @ConditionalOnBean(name = "streamingWebClient")
  @ConditionalOnProperty(
      prefix = "patra.rest-client.streaming",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  static class HttpStreamingDownloaderConfiguration {

    /// HTTP/HTTPS 流式下载策略。
    ///
    /// @param webClient streamingWebClient
    /// @param properties 下载配置
    /// @return HTTP/HTTPS 流式下载策略
    @Bean
    @ConditionalOnMissingBean(HttpStreamingDownloader.class)
    public StreamingDownloader httpStreamingDownloader(
        @Qualifier("streamingWebClient")
            org.springframework.web.reactive.function.client.WebClient webClient,
        DownloadProperties properties) {
      return new HttpStreamingDownloader(webClient, properties);
    }
  }
}
