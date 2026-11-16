package dev.patra.tool.test.archunit.fixtures.persistence.jpaexecuteupdate.infra.note.persistence;

import jakarta.persistence.EntityManager;

/// 夹具：适配器里用 `EntityManager` 执行批量写，违规。
public class NoteRepositoryAdapter {

  private final EntityManager entityManager;

  NoteRepositoryAdapter(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  /// 改版本。
  public int touch() {
    return entityManager
        .createQuery("UPDATE NoteEntity n SET n.version = n.version + 1")
        .executeUpdate();
  }
}
