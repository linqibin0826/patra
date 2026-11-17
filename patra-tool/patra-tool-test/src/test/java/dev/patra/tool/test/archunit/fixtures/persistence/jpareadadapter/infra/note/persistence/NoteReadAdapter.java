package dev.patra.tool.test.archunit.fixtures.persistence.jpareadadapter.infra.note.persistence;

import dev.patra.tool.test.archunit.fixtures.persistence.jpareadadapter.domain.note.port.read.NoteReadPort;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.simple.JdbcClient;

/// 夹具：读适配器用 `JdbcClient` 和原生查询读库，合规。
public class NoteReadAdapter implements NoteReadPort {

  private final JdbcClient jdbc;
  private final EntityManager entityManager;

  NoteReadAdapter(JdbcClient jdbc, EntityManager entityManager) {
    this.jdbc = jdbc;
    this.entityManager = entityManager;
  }

  @Override
  public long count() {
    entityManager.createNativeQuery("SELECT 1").getSingleResult();
    return jdbc.sql("SELECT count(*) FROM note").query(Long.class).single();
  }
}
