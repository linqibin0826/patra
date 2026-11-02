package dev.patra.analytics.infra.insight.config;

import com.clickhouse.client.api.Client;
import dev.patra.analytics.infra.insight.catalog.CachingPublicationNeighborsDecorator;
import dev.patra.analytics.infra.insight.catalog.PublicationCatalogAdapter;
import dev.patra.analytics.infra.insight.signal.ClickHouseReadingSignalAdapter;
import dev.patra.analytics.infra.insight.stats.CachingReaderStatsDecorator;
import dev.patra.analytics.infra.insight.stats.ClickHouseReaderStatsAdapter;
import dev.patra.catalog.api.publication.endpoint.PublicationEndpoint;
import dev.patra.catalog.api.publication.endpoint.PublicationNeighborEndpoint;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

/// insight 基础设施的装配：ClickHouse 适配器与 catalog 适配器各自包一层 Redis 缓存装饰器，对外只暴露装饰器。
///
/// 被装饰的适配器不注册成 Bean，免得同一端口有两个候选。
@Configuration
@EnableConfigurationProperties(InsightProperties.class)
public class InsightInfraConfiguration {

  /// 计数：ClickHouse 查询外面套 60 秒缓存。
  ///
  /// @param client ClickHouse 客户端
  /// @param redis Redis 客户端
  /// @param properties insight 配置
  /// @param meters 指标
  /// @return 计数端口
  @Bean
  public CachingReaderStatsDecorator readerStatsPort(
      Client client,
      StringRedisTemplate redis,
      InsightProperties properties,
      MeterRegistry meters) {
    return new CachingReaderStatsDecorator(
        new ClickHouseReaderStatsAdapter(client, properties), redis, properties, meters);
  }

  /// 排名信号：直接查 ClickHouse（只有定时任务用）。
  ///
  /// @param client ClickHouse 客户端
  /// @param properties insight 配置
  /// @return 信号端口
  @Bean
  public ClickHouseReadingSignalAdapter readingSignalPort(
      Client client, InsightProperties properties) {
    return new ClickHouseReadingSignalAdapter(client, properties);
  }

  /// catalog：同主题邻居套 6 小时缓存。
  ///
  /// @param publications 文献解析接口代理
  /// @param neighbors 文献邻居接口代理
  /// @param redis Redis 客户端
  /// @param json JSON 映射器
  /// @param meters 指标
  /// @return catalog 端口
  @Bean
  public CachingPublicationNeighborsDecorator publicationCatalogPort(
      PublicationEndpoint publications,
      PublicationNeighborEndpoint neighbors,
      StringRedisTemplate redis,
      JsonMapper json,
      MeterRegistry meters) {
    return new CachingPublicationNeighborsDecorator(
        new PublicationCatalogAdapter(publications, neighbors), redis, json, meters);
  }
}
