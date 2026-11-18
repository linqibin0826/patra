package dev.patra.tool.test.archunit.fixtures.persistence.jpajdbcwrite.infra.note.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;

/// 夹具：JPA 服务的仓储用 `JdbcClient` 写库，违规。
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
