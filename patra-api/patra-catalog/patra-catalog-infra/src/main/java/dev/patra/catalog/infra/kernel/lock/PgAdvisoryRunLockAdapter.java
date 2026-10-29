package dev.patra.catalog.infra.kernel.lock;

import dev.patra.catalog.domain.kernel.port.lock.ExclusiveRunPort;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/// 基于 PostgreSQL 会话级 advisory lock 的互斥运行。
///
/// 在同一个连接上 `pg_try_advisory_lock` → 执行工作 → `pg_advisory_unlock`；连接在整次运行期间
/// 一直占用（三个任务各占一条，连接池够用）。进程异常退出时连接断开，锁随会话自动释放。
///
/// @author linqibin
/// @since 0.11.0
@Slf4j
@Component
@RequiredArgsConstructor
public class PgAdvisoryRunLockAdapter implements ExclusiveRunPort {

  private final JdbcTemplate jdbcTemplate;

  /// 取到锁就执行并返回结果，取不到返回空。
  ///
  /// @param lockName 锁名
  /// @param work 要执行的工作
  /// @param <T> 结果类型
  /// @return 结果；锁被别的实例持有时为空
  @Override
  public <T> Optional<T> runExclusively(String lockName, Supplier<T> work) {
    long key = lockKey(lockName);
    return jdbcTemplate.execute(
        (ConnectionCallback<Optional<T>>)
            connection -> {
              if (!queryBoolean(connection, "SELECT pg_try_advisory_lock(?)", key)) {
                log.info("任务锁 {} 已被其他实例持有，本次跳过", lockName);
                return Optional.empty();
              }
              try {
                return Optional.ofNullable(work.get());
              } finally {
                queryBoolean(connection, "SELECT pg_advisory_unlock(?)", key);
              }
            });
  }

  /// 锁名到 64 位锁键：名字 UUID（v3）的高 64 位，在 Java 端算好再传入。
  ///
  /// @param lockName 锁名
  /// @return 锁键
  static long lockKey(String lockName) {
    return UUID.nameUUIDFromBytes(lockName.getBytes(StandardCharsets.UTF_8))
        .getMostSignificantBits();
  }

  /// 执行返回单个布尔值的语句。
  private static boolean queryBoolean(Connection connection, String sql, long key)
      throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(sql)) {
      ps.setLong(1, key);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() && rs.getBoolean(1);
      }
    }
  }
}
