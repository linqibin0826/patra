package com.patra.catalog.domain.port;

import com.patra.catalog.domain.model.aggregate.MeshQualifierAggregate;
import java.util.List;

/// MeSH 限定词聚合根仓储接口(领域层定义,基础设施层实现)。
///
/// **设计原则**：
///
/// - 接口在Domain层定义,确保领域层独立
///   - 实现在Infrastructure层,遵循依赖倒置原则(DIP)
///   - 限定词是独立的主数据,不依赖其他实体
///   - 约 80 个限定词,数量稳定
///
/// @author linqibin
/// @since 0.1.0
public interface MeshQualifierRepository {

  /// 批量保存限定词聚合根。
  ///
  /// 实现说明：
  ///
  /// - 保存完整的限定词聚合根(约 80 条记录)
  ///   - 使用 MyBatis-Plus 的 saveBatch 方法进行批量插入
  ///   - 限定词是独立的主数据,必须先于主题词导入
  ///   - 事务由调用方(Application 层)管理
  ///
  /// @param qualifiers 限定词聚合根列表
  void saveBatch(List<MeshQualifierAggregate> qualifiers);

  /// 清空所有限定词数据。
  ///
  /// **警告**：此方法使用 TRUNCATE TABLE，是 DDL 操作，会隐式提交事务，无法回滚。
  ///
  /// 使用场景：
  ///
  /// - TRUNCATE_REIMPORT 模式下，先清空再重新导入
  /// - 仅用于限定词导入场景，不应在其他业务逻辑中调用
  void truncateAll();
}
