package dev.patra.tool.test.archunit.fixtures.persistence.jpamodifyingflags.infra.note.persistence.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/// 夹具：`@Modifying` 没带 `flushAutomatically` / `clearAutomatically`，违规。
public interface NoteDao extends JpaRepository<Object, Long> {

  /// 改版本。
  @Modifying
  @Query("UPDATE NoteEntity n SET n.version = n.version + 1")
  int touch();
}
