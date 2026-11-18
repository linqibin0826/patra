package dev.patra.tool.test.archunit.fixtures.persistence.sqljdbcoutside.infra.note.lock;

import org.springframework.jdbc.core.simple.JdbcClient;

/// 夹具：`JdbcClient` 出现在 persistence 包以外，违规。
public class NoteLockAdapter {

  private final JdbcClient jdbc;

  NoteLockAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /// 试着加锁。
  public boolean tryLock(long key) {
    return jdbc.sql("SELECT pg_try_advisory_lock(:key)")
        .param("key", key)
        .query(Boolean.class)
        .single();
  }
}
