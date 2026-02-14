package com.patra.catalog.infra.persistence.dao;

import com.patra.catalog.infra.persistence.entity.PublicationTypeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/// 文献出版类型关联 JPA Repository。
///
/// **功能说明**：
///
/// 提供文献出版类型关联的数据访问接口，基于 Spring Data JPA 自动实现。
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationTypeDao extends JpaRepository<PublicationTypeEntity, Long> {

  /// 按出版物 ID 查询出版类型列表。
  ///
  /// @param publicationId 出版物 ID
  /// @return 出版类型实体列表
  List<PublicationTypeEntity> findByPublicationId(Long publicationId);

  /// 按出版物 ID 删除所有出版类型（用于 DELETE/INSERT 模式）。
  ///
  /// @param publicationId 出版物 ID
  void deleteByPublicationId(Long publicationId);
}
