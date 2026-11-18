package dev.patra.tool.test.archunit.fixtures.persistence.jpamodifyingnoquery.infra.note.persistence.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

/// 夹具：`@Modifying` 挂在派生删除上、没有 `@Query`，违规。
public interface NoteDao extends JpaRepository<Object, Long> {

  /// 按版本删。
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  void deleteAllByVersion(Long version);
}
