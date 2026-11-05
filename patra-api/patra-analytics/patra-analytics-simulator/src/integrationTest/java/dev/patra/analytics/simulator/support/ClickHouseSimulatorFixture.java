package dev.patra.analytics.simulator.support;

import com.clickhouse.client.api.Client;
import dev.patra.analytics.infra.ingest.support.ClickHouseMigrator;
import dev.patra.tool.test.container.initializer.ClickHouseContainerInitializer;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.testcontainers.clickhouse.ClickHouseContainer;

/// 模拟器集成测试的 ClickHouse：每个用例一个新库，用服务自己的迁移器建表，停掉后台合并（重复行不被合掉，条数断言才稳定）。
public final class ClickHouseSimulatorFixture {

  private static final String[] TABLES = {
    "events", "article_session_reads", "session_quality", "daily_person_activity"
  };

  private ClickHouseSimulatorFixture() {}

  /// 新建库、迁移、停合并，返回默认库指向它的客户端。
  ///
  /// @return 客户端
  public static Client migratedDatabaseWithoutMerges() {
    ClickHouseContainer container = ClickHouseContainerInitializer.getClickHouseContainer();
    String url = "http://" + container.getHost() + ":" + container.getMappedPort(8123);
    String database = "sim_" + UUID.randomUUID().toString().replace("-", "");
    try (Client admin = client(url, container, "default")) {
      execute(admin, "CREATE DATABASE " + database);
    }
    Client client = client(url, container, database);
    new ClickHouseMigrator(client).migrate();
    for (String table : TABLES) {
      execute(client, "SYSTEM STOP MERGES " + table);
    }
    return client;
  }

  /// 查一个数。
  ///
  /// @param client 客户端
  /// @param sql 返回单列 `n` 的查询
  /// @return 数
  public static long number(Client client, String sql) {
    return client.queryAll(sql).getFirst().getLong("n");
  }

  private static Client client(String url, ClickHouseContainer container, String database) {
    return new Client.Builder()
        .addEndpoint(url)
        .setUsername(container.getUsername())
        .setPassword(container.getPassword())
        .setDefaultDatabase(database)
        .compressClientRequest(true)
        .setSocketTimeout(60, ChronoUnit.SECONDS)
        .build();
  }

  private static void execute(Client client, String sql) {
    try {
      client.execute(sql).get(60, TimeUnit.SECONDS).close();
    } catch (Exception e) {
      throw new IllegalStateException("执行失败：" + sql, e);
    }
  }
}
