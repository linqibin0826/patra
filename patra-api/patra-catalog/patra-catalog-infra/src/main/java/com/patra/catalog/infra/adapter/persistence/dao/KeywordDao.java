package com.patra.catalog.infra.adapter.persistence.dao;

import com.patra.catalog.infra.adapter.persistence.entity.KeywordEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/// 关键词 JPA Repository。
///
/// **功能说明**：
///
/// 提供关键词的数据访问接口，基于 Spring Data JPA 自动实现。
/// 支持规范化去重和频次统计。
///
/// @author linqibin
/// @since 0.1.0
public interface KeywordDao extends JpaRepository<KeywordEntity, Long> {

  /// 按规范化词形查询关键词。
  ///
  /// @param normalizedTerm 规范化词形
  /// @return 关键词实体（如果存在）
  Optional<KeywordEntity> findByNormalizedTerm(String normalizedTerm);

  /// 按规范化词形和来源查询关键词。
  ///
  /// @param normalizedTerm 规范化词形
  /// @param source 来源
  /// @return 关键词实体（如果存在）
  Optional<KeywordEntity> findByNormalizedTermAndSource(String normalizedTerm, String source);

  /// 按规范化词形批量查询关键词。
  ///
  /// @param normalizedTerms 规范化词形列表
  /// @return 关键词实体列表
  List<KeywordEntity> findByNormalizedTermIn(List<String> normalizedTerms);
}
