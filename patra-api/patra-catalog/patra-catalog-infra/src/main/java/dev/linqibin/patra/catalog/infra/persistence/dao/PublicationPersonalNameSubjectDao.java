package dev.linqibin.patra.catalog.infra.persistence.dao;

import dev.linqibin.patra.catalog.infra.persistence.entity.PublicationPersonalNameSubjectEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 人物主题 JPA Repository。
///
/// **职责**：
///
/// - 提供人物主题实体的 CRUD 操作
/// - 支持按出版物 ID 查询和删除（DELETE/INSERT 模式）
/// - 支持批量删除以优化批处理性能
///
/// **使用模式**：
///
/// 采用 DELETE/INSERT 模式管理关联数据：
/// 1. 删除某文献的所有人物主题
/// 2. 重新插入新的人物主题记录
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationPersonalNameSubjectDao
    extends JpaRepository<PublicationPersonalNameSubjectEntity, Long> {

  /// 根据出版物 ID 查询人物主题。
  ///
  /// @param publicationId 出版物 ID
  /// @return 人物主题列表
  List<PublicationPersonalNameSubjectEntity> findByPublicationId(Long publicationId);

  /// 根据出版物 ID 删除人物主题。
  ///
  /// DELETE/INSERT 模式的第一步：清除现有记录。
  ///
  /// @param publicationId 出版物 ID
  void deleteByPublicationId(Long publicationId);

  /// 批量根据出版物 ID 删除人物主题。
  ///
  /// 用于批量导入时的高效删除，比循环单条删除性能更好。
  ///
  /// @param publicationIds 出版物 ID 集合
  @Modifying
  @Query(
      "DELETE FROM PublicationPersonalNameSubjectEntity e WHERE e.publicationId IN :publicationIds")
  void deleteByPublicationIdIn(@Param("publicationIds") Collection<Long> publicationIds);
}
