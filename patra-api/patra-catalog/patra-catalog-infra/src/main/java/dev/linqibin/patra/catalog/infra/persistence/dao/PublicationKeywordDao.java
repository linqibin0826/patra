package dev.linqibin.patra.catalog.infra.persistence.dao;

import dev.linqibin.patra.catalog.infra.persistence.entity.PublicationKeywordEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/// 文献关键词关联 JPA Repository。
///
/// **功能说明**：
///
/// 提供文献关键词关联的数据访问接口，基于 Spring Data JPA 自动实现。
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationKeywordDao extends JpaRepository<PublicationKeywordEntity, Long> {

  /// 按出版物 ID 查询关键词列表。
  ///
  /// @param publicationId 出版物 ID
  /// @return 关键词实体列表
  List<PublicationKeywordEntity> findByPublicationId(Long publicationId);

  /// 按出版物 ID 删除所有关键词（用于 DELETE/INSERT 模式）。
  ///
  /// @param publicationId 出版物 ID
  void deleteByPublicationId(Long publicationId);
}
