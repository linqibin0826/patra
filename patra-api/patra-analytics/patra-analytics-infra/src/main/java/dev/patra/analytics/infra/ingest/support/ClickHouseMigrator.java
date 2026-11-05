package dev.patra.analytics.infra.ingest.support;

import com.clickhouse.client.api.Client;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/// ClickHouse 的建表迁移：按编号执行 classpath `clickhouse/V{n}__*.sql` 里还没执行过的脚本，记进 `schema_migrations`。
///
/// 与 golang-migrate 的做法相同：脚本只增不改；一个脚本可含多条语句，以 `;` 加换行分隔。版本号在整个脚本执行完后才记，
/// 所以脚本里的 DDL 一律写 `IF NOT EXISTS`：中途失败（超时、进程被杀）后下次启动从头重跑这个脚本也安全。
/// 不在启动时同步执行：由 boot 的 `IngestStartup` 在后台重试，成功后才启动入库消费者，ClickHouse 连不上时服务照常起来。
@Slf4j
public class ClickHouseMigrator {

  private static final Pattern SCRIPT_NAME = Pattern.compile("V(\\d+)__.+\\.sql");

  private static final long TIMEOUT_SECONDS = 60;

  private final Client client;

  /// @param client 默认库指向目标库的客户端
  public ClickHouseMigrator(Client client) {
    this.client = client;
  }

  /// 执行没执行过的脚本，按编号升序。
  ///
  /// @throws IllegalStateException 有语句执行失败（连不上、超时、SQL 错误）
  public void migrate() {
    execute(
        "CREATE TABLE IF NOT EXISTS schema_migrations"
            + " (version UInt32, applied_at DateTime64(3, 'UTC') DEFAULT now64(3))"
            + " ENGINE = MergeTree ORDER BY version");
    Set<Long> applied =
        client.queryAll("SELECT version FROM schema_migrations").stream()
            .map(row -> row.getLong("version"))
            .collect(Collectors.toSet());
    for (Script script : scripts()) {
      if (applied.contains(script.version())) {
        continue;
      }
      script.statements().forEach(this::execute);
      execute("INSERT INTO schema_migrations (version) VALUES (" + script.version() + ")");
      log.info("ClickHouse 迁移已执行: V{}", script.version());
    }
  }

  /// 读出全部脚本，按编号排序。只读 classpath 上第一个 `clickhouse/` 目录：测试类路径上 infra 的资源可能出现两次。
  ///
  /// @return 脚本
  private static List<Script> scripts() {
    try {
      Resource[] resources =
          new PathMatchingResourcePatternResolver().getResources("classpath:clickhouse/V*.sql");
      return Arrays.stream(resources)
          .map(ClickHouseMigrator::toScript)
          .sorted(Comparator.comparingLong(Script::version))
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("读 ClickHouse 迁移脚本失败", e);
    }
  }

  /// 解析一个脚本：编号取自文件名，语句以 `;` 加换行分隔，空段丢掉。
  ///
  /// @param resource 脚本文件
  /// @return 脚本
  private static Script toScript(Resource resource) {
    Matcher matcher = SCRIPT_NAME.matcher(String.valueOf(resource.getFilename()));
    if (!matcher.matches()) {
      throw new IllegalStateException("ClickHouse 迁移脚本名不合规: " + resource.getFilename());
    }
    try (InputStream in = resource.getInputStream()) {
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      List<String> statements =
          Arrays.stream(text.split(";\\s*\\n"))
              .map(String::strip)
              .filter(s -> !s.isEmpty())
              .toList();
      return new Script(Long.parseLong(matcher.group(1)), statements);
    } catch (IOException e) {
      throw new UncheckedIOException("读 ClickHouse 迁移脚本失败: " + resource.getFilename(), e);
    }
  }

  /// 同步执行一条语句。
  ///
  /// @param sql 语句
  private void execute(String sql) {
    try {
      client.execute(sql).get(TIMEOUT_SECONDS, TimeUnit.SECONDS).close();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("ClickHouse 迁移被中断", e);
    } catch (Exception e) {
      throw new IllegalStateException("ClickHouse 迁移语句执行失败: " + sql, e);
    }
  }

  /// 一个迁移脚本。
  ///
  /// @param version 编号
  /// @param statements 语句
  private record Script(long version, List<String> statements) {
    private Script {
      statements = List.copyOf(statements);
    }
  }
}
