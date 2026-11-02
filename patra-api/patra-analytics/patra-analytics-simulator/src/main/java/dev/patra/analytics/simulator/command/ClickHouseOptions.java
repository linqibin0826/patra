package dev.patra.analytics.simulator.command;

import com.clickhouse.client.api.Client;
import java.time.temporal.ChronoUnit;
import picocli.CommandLine.Option;

/// ClickHouse 连接。密码只读环境变量 `CLICKHOUSE_PATRA_PASSWORD`，不收命令行参数（不进 shell 历史、不写进 params.json）。
public final class ClickHouseOptions {

  /// 密码的环境变量名（与 `~/.patra/secrets/clickhouse.env` 一致）
  static final String PASSWORD_ENV = "CLICKHOUSE_PATRA_PASSWORD";

  @Option(
      names = "--clickhouse-url",
      description = "ClickHouse HTTP 地址，如 http://patra-clickhouse:8123")
  String url;

  @Option(
      names = "--clickhouse-user",
      defaultValue = "patra",
      description = "账号，默认 ${DEFAULT-VALUE}")
  String user;

  @Option(
      names = "--clickhouse-database",
      defaultValue = "patra_analytics",
      description = "库，默认 ${DEFAULT-VALUE}")
  String database;

  /// 是否给了地址。
  ///
  /// @return 是否
  boolean present() {
    return url != null;
  }

  /// 建客户端（开请求压缩；读写超时 10 分钟，够一块 25 万行的写入与对账查询）。
  ///
  /// @return 客户端
  /// @throws IllegalArgumentException 没给地址或缺密码环境变量
  Client client() {
    if (url == null) {
      throw new IllegalArgumentException("要给 --clickhouse-url");
    }
    String password = System.getenv(PASSWORD_ENV);
    if (password == null) {
      throw new IllegalArgumentException(
          "缺环境变量 " + PASSWORD_ENV + "（见 ~/.patra/secrets/clickhouse.env）");
    }
    return new Client.Builder()
        .addEndpoint(url)
        .setUsername(user)
        .setPassword(password)
        .setDefaultDatabase(database)
        .compressClientRequest(true)
        .setConnectTimeout(5, ChronoUnit.SECONDS)
        .setSocketTimeout(10, ChronoUnit.MINUTES)
        .build();
  }
}
