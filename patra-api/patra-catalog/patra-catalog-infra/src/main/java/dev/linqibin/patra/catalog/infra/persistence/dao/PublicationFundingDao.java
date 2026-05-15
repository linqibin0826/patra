package dev.linqibin.patra.catalog.infra.persistence.dao;

import dev.linqibin.patra.catalog.infra.persistence.entity.PublicationFundingEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/// 文献资助信息关联 JPA Repository。
///
/// **功能说明**：
///
/// 提供文献资助信息关联的数据访问接口，基于 Spring Data JPA 自动实现。
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationFundingDao extends JpaRepository<PublicationFundingEntity, Long> {

  /// 按出版物 ID 查询资助信息列表。
  ///
  /// @param publicationId 出版物 ID
  /// @return 资助信息实体列表
  List<PublicationFundingEntity> findByPublicationId(Long publicationId);

  /// 按出版物 ID 删除所有资助信息（用于 DELETE/INSERT 模式）。
  ///
  /// @param publicationId 出版物 ID
  void deleteByPublicationId(Long publicationId);
}
