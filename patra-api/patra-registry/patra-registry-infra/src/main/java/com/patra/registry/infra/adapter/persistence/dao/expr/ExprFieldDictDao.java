package com.patra.registry.infra.adapter.persistence.dao.expr;

import com.patra.registry.infra.adapter.persistence.entity.expr.ExprFieldDictEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/// 表达式字段字典 JPA Repository。
///
/// **职责**：
///
/// - 提供 ExprFieldDictEntity 的 CRUD 操作
/// - 查询所有激活的表达式字段
///
/// @author linqibin
/// @since 0.1.0
public interface ExprFieldDictDao extends JpaRepository<ExprFieldDictEntity, Long> {

  /// 查询所有激活的表达式字段。
  ///
  /// @return 表达式字段列表
  @Query(
      """
      SELECT e FROM ExprFieldDictEntity e
      WHERE e.deletedAt IS NULL
      ORDER BY e.fieldKey
      """)
  List<ExprFieldDictEntity> findAllActive();
}
