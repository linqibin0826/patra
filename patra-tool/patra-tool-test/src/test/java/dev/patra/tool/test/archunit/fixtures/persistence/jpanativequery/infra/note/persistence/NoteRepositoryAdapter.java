package dev.patra.tool.test.archunit.fixtures.persistence.jpanativequery.infra.note.persistence;

import jakarta.persistence.EntityManager;

/// 夹具：仓储里用原生查询读库，违规（只读的原生查询放 Dao 或读适配器）。
public class NoteRepositoryAdapter {

  private final EntityManager entityManager;

  NoteRepositoryAdapter(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  /// 数笔记。
  public Object count() {
    return entityManager.createNativeQuery("SELECT count(*) FROM note").getSingleResult();
  }
}
