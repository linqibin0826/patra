package dev.patra.catalog.infra.publication.persistence.dao;

import dev.patra.catalog.infra.publication.persistence.entity.PublicationAuthorEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 文献-作者关联 JPA Repository（批量写 + 恢复读）。
///
/// **职责**：
///
/// - 管理 `PublicationAuthorEntity` 的批量写入与按文献恢复
/// - 重导时按文献维度整体替换（先删后插），避免撞 `uk_author_order`
///
/// @author linqibin
/// @since 0.6.0
public interface PublicationAuthorDao extends JpaRepository<PublicationAuthorEntity, Long> {

  /// 按出版物 ID 查作者行，按 `author_order` 升序（聚合恢复用）。
  ///
  /// @param publicationId 出版物 ID
  /// @return 作者关联实体列表（按作者顺序升序）
  List<PublicationAuthorEntity> findAllByPublicationIdOrderByAuthorOrderAsc(Long publicationId);

  /// 按出版物 ID 列表批量删除（重导替换语义用）。
  ///
  /// 采用 bulk DML 而非派生删除：DELETE 立即下发，不进入 Hibernate 动作队列，
  /// 从而避开「同一次 flush 内 INSERT 先于 DELETE」导致新行撞 `uk_author_order` 的隐患。
  /// 实体继承 `ValueObjectJpaEntity`（无 `@Version` / 无软删除），bulk DML 无副作用。
  ///
  /// @param publicationIds 出版物 ID 列表
  @Modifying
  @Query("DELETE FROM PublicationAuthorEntity e WHERE e.publicationId IN :publicationIds")
  void deleteAllByPublicationIdIn(@Param("publicationIds") Collection<Long> publicationIds);
}
