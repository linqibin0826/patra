package com.patra.catalog.infra.adapter.persistence.dao;

import com.patra.catalog.infra.adapter.persistence.entity.PublicationInvestigatorEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 文献-研究者关联 JPA Repository。
///
/// **职责**：
///
/// - 提供文献-研究者关联实体的 CRUD 操作
/// - 支持按出版物 ID 查询和删除（DELETE/INSERT 模式）
/// - 支持批量删除以优化批处理性能
///
/// **使用模式**：
///
/// 采用 DELETE/INSERT 模式管理关联数据：
/// 1. 删除某文献的所有研究者关联
/// 2. 重新插入新的关联记录
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationInvestigatorDao
    extends JpaRepository<PublicationInvestigatorEntity, Long> {

  /// 根据出版物 ID 查询研究者关联。
  ///
  /// @param publicationId 出版物 ID
  /// @return 研究者关联列表
  List<PublicationInvestigatorEntity> findByPublicationId(Long publicationId);

  /// 根据出版物 ID 删除研究者关联。
  ///
  /// DELETE/INSERT 模式的第一步：清除现有关联。
  ///
  /// @param publicationId 出版物 ID
  void deleteByPublicationId(Long publicationId);

  /// 批量根据出版物 ID 删除研究者关联。
  ///
  /// 用于批量导入时的高效删除，比循环单条删除性能更好。
  ///
  /// @param publicationIds 出版物 ID 集合
  @Modifying
  @Query("DELETE FROM PublicationInvestigatorEntity e WHERE e.publicationId IN :publicationIds")
  void deleteByPublicationIdIn(@Param("publicationIds") Collection<Long> publicationIds);
}
