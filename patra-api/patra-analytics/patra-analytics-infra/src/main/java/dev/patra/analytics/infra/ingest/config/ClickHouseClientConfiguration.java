package dev.patra.analytics.infra.ingest.config;

import com.clickhouse.client.api.Client;
import dev.patra.analytics.infra.ingest.support.ClickHouseMigrator;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// ClickHouse 客户端与建表迁移的装配。
@Configuration
@EnableConfigurationProperties(ClickHouseProperties.class)
public class ClickHouseClientConfiguration {

  /// 官方 client-v2（HTTP 协议）。读超时 30 秒（一批 5 万条的写入在这之内），连接超时 5 秒。
  ///
  /// @param properties 连接配置
  /// @return 客户端
  @Bean(destroyMethod = "close")
  public Client clickHouseClient(ClickHouseProperties properties) {
    return new Client.Builder()
        .addEndpoint(properties.url())
        .setUsername(properties.username())
        .setPassword(properties.password())
        .setDefaultDatabase(properties.database())
        .setSocketTimeout(30, ChronoUnit.SECONDS)
        .setConnectTimeout(5, ChronoUnit.SECONDS)
        .build();
  }

  /// 启动时执行没执行过的建表脚本。
  ///
  /// @param client 客户端
  /// @return 迁移器
  @Bean
  public ClickHouseMigrator clickHouseMigrator(Client client) {
    return new ClickHouseMigrator(client);
  }
}
