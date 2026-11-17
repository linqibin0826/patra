package dev.patra.tool.test.archunit.fixtures.persistence.jpaexempt.infra.kernel.lock;

import org.springframework.jdbc.core.JdbcTemplate;

/// 夹具：只执行 advisory lock 的适配器，登记豁免后合规。
public class RunLockAdapter {

  private final JdbcTemplate jdbc;

  RunLockAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /// 试着加锁。
  public boolean tryLock(long key) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject("SELECT pg_try_advisory_lock(?)", Boolean.class, key));
  }
}
