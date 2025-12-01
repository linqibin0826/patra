package com.patra.starter.restclient.config;

import com.patra.starter.restclient.download.CompositeProgressListener;
import com.patra.starter.restclient.download.DefaultDownloadClient;
import com.patra.starter.restclient.download.DownloadClient;
import com.patra.starter.restclient.download.LoggingProgressListener;
import com.patra.starter.restclient.download.MetricsProgressListener;
import com.patra.starter.restclient.download.ProgressListener;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/// 下载客户端自动配置。
///
/// 自动配置支持进度监控的下载客户端和默认监听器。
///
/// **配置的 Bean**：
/// - `downloadClient`：使用 longRunningRestClient 的下载客户端
/// - `defaultProgressListener`：组合日志和指标监听器
///
/// **依赖条件**：
/// - 需要 `longRunningRestClient` Bean（由 RestClientAutoConfiguration 提供）
/// - 如果存在 MeterRegistry，会自动添加 MetricsProgressListener
///
/// @author linqibin
/// @since 0.1.0
@AutoConfiguration(after = RestClientAutoConfiguration.class)
@ConditionalOnClass(RestClient.class)
public class DownloadClientAutoConfiguration {

  /// 默认日志输出间隔（每 10% 输出一次）
  private static final int DEFAULT_LOG_INTERVAL_PERCENT = 10;

  /// 创建下载客户端。
  ///
  /// @param restClient 长时间运行 RestClient（10 分钟读取超时）
  /// @return 下载客户端
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(name = "longRunningRestClient")
  public DownloadClient downloadClient(@Qualifier("longRunningRestClient") RestClient restClient) {
    return new DefaultDownloadClient(restClient);
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
}
