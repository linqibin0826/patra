package dev.linqibin.starter.restclient.config;

import dev.linqibin.starter.restclient.download.CompositeProgressListener;
import dev.linqibin.starter.restclient.download.DefaultDownloadClient;
import dev.linqibin.starter.restclient.download.DownloadClient;
import dev.linqibin.starter.restclient.download.LoggingProgressListener;
import dev.linqibin.starter.restclient.download.MetricsProgressListener;
import dev.linqibin.starter.restclient.download.ProgressListener;
import dev.linqibin.starter.restclient.download.strategy.FtpStreamingDownloader;
import dev.linqibin.starter.restclient.download.strategy.HttpStreamingDownloader;
import dev.linqibin.starter.restclient.download.strategy.StreamingDownloader;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
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
    prefix = "linqibin.starter.rest-client.download",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(DownloadProperties.class)
public class DownloadClientAutoConfiguration {

  /// 默认日志输出间隔（每 10% 输出一次）
  private static final int DEFAULT_LOG_INTERVAL_PERCENT = 10;

  /// 创建下载客户端。
  ///
  /// 使用 `ObjectProvider` 解析 `StreamingDownloader` 集合，避免
  /// `@ConditionalOnBean(StreamingDownloader.class)` 在同类内兄弟 `@Bean`
  /// 提供 bean 时不可靠的问题 —— `OnBeanCondition` 在解析阶段评估，
  /// 只能看到"截止当前已处理"的 BeanDefinition，同类兄弟 `@Bean` 反射注册
  /// 顺序不确定（可能尚未注册）。这是 Spring 一贯设计，团队明确拒绝修复
  /// （见 spring-boot#30508 / #26621 / #37382），推荐 `ObjectProvider`
  /// 模式把解析推迟到 BeanFactory 完成阶段。
  ///
  /// 无 downloader 时 `DefaultDownloadClient` 在调用期才抛 `unsupportedScheme`，
  /// 由调用方处理。
  ///
  /// @param properties 下载配置
  /// @param downloadersProvider 流式下载策略 ObjectProvider
  /// @return 下载客户端
  @Bean
  @ConditionalOnMissingBean
  public DownloadClient downloadClient(
      DownloadProperties properties, ObjectProvider<StreamingDownloader> downloadersProvider) {
    return new DefaultDownloadClient(downloadersProvider.orderedStream().toList(), properties);
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
      prefix = "linqibin.starter.rest-client.download.ftp",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public StreamingDownloader ftpStreamingDownloader(DownloadProperties properties) {
    return new FtpStreamingDownloader(properties);
  }

  /// HTTP/HTTPS 流式下载策略配置。
  ///
  /// 移除了原 `@ConditionalOnBean(name = "streamingWebClient")` —— 该守卫受
  /// `OnBeanCondition` 评估时序约束（解析阶段评估，跨类依赖目标 BeanDefinition
  /// 已注册），存在不可靠风险。本类的进入条件（`@ConditionalOnClass(WebClient)`
  /// + `streaming.enabled`）与 `StreamingWebClientAutoConfiguration` 完全一致，
  /// 前者满足则 `streamingWebClient` bean 一定存在，守卫冗余。若用户手动排除
  /// `StreamingWebClientAutoConfiguration`，`@Qualifier` 注入会失败并给出清晰报错。
  @Configuration
  @ConditionalOnClass(name = "org.springframework.web.reactive.function.client.WebClient")
  @ConditionalOnProperty(
      prefix = "linqibin.starter.rest-client.streaming",
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
