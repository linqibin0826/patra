package dev.patra.tool.test.archunit.fixtures.persistence.sqlcompliant.infra.note.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;

/// 夹具：原生 SQL 服务的仓储在 persistence 包里用 `JdbcClient`，合规。
public class NoteRepositoryAdapter {

  private final JdbcClient jdbc;

  NoteRepositoryAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /// 删笔记。
  public void delete(long id) {
    jdbc.sql("DELETE FROM note WHERE id = :id").param("id", id).update();
  }
}
