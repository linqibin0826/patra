package dev.patra.tool.test.archunit.fixtures.persistence.jpacompliant.infra.note.persistence.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/// 夹具：批量语句是 Dao 上的 `@Query`，带两个属性，合规。
public interface NoteDao extends JpaRepository<Object, Long> {

  /// 改标题。
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("UPDATE NoteEntity n SET n.version = n.version + 1")
  int touch();

  /// 派生删除不挂 `@Modifying`，走实体。
  void deleteAllByVersion(Long version);
}
