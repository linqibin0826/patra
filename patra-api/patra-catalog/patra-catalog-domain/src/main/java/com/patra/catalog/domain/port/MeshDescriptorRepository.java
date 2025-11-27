package com.patra.catalog.domain.port;

import com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import com.patra.catalog.domain.model.entity.MeshConcept;
import com.patra.catalog.domain.model.entity.MeshEntryTerm;
import com.patra.catalog.domain.model.entity.MeshTreeNumber;
import java.util.List;

/// MeSH 主题词聚合根仓储接口(领域层定义,基础设施层实现)。
///
/// **设计原则**：
///
/// - 接口在Domain层定义,确保领域层独立
///   - 实现在Infrastructure层,遵循依赖倒置原则(DIP)
///   - 聚合内实体(TreeNumber/EntryTerm/Concept)分开加载
///   - 提供树形查询、全文检索等专门方法
///
/// @author linqibin
/// @since 0.1.0
public interface MeshDescriptorRepository {

  /// 批量保存主题词聚合根。
  ///
  /// 实现说明：
  ///
  /// - 保存完整的聚合根（包括所有子实体：TreeNumber、EntryTerm、Concept）
  ///   - 使用 MyBatis-Plus 的 saveBatch 方法进行批量插入
  ///   - 子实体通过 descriptor_ui 关联（MeSH 原生 UI 标识符）
  ///   - 事务由调用方（Application 层）管理
  ///
  /// @param descriptors 主题词聚合根列表
  void saveBatch(List<MeshDescriptorAggregate> descriptors);

  /// 批量保存树形编号实体。
  ///
  /// 实现说明：
  ///
  /// - 直接批量插入到 cat_mesh_tree_number 表
  ///   - 通过 descriptor_ui 关联主表（MeSH 原生 UI 标识符）
  ///   - 用于性能优化场景（分开导入主表和子表）
  ///
  /// @param treeNumbers 树形编号实体列表
  void saveTreeNumbersBatch(List<MeshTreeNumber> treeNumbers);

  /// 批量保存入口术语实体。
  ///
  /// 实现说明：
  ///
  /// - 直接批量插入到 cat_mesh_entry_term 表
  ///   - 通过 descriptor_ui 关联主表（MeSH 原生 UI 标识符）
  ///   - 用于性能优化场景（分开导入主表和子表）
  ///
  /// @param entryTerms 入口术语实体列表
  void saveEntryTermsBatch(List<MeshEntryTerm> entryTerms);

  /// 批量保存概念实体。
  ///
  /// 实现说明：
  ///
  /// - 直接批量插入到 cat_mesh_concept 表
  ///   - 通过 descriptor_ui 关联主表（MeSH 原生 UI 标识符）
  ///   - 用于性能优化场景（分开导入主表和子表）
  ///
  /// @param concepts 概念实体列表
  void saveConceptsBatch(List<MeshConcept> concepts);

  /// 清空所有 MeSH 主题词数据（TRUNCATE TABLE）。
  ///
  /// 实现说明：
  ///
  /// - 使用 TRUNCATE TABLE 清空所有 MeSH 相关表
  ///   - 执行顺序：先清空子表（TreeNumber、EntryTerm、Concept），再清空主表（Descriptor）
  ///   - 用于全量重导入场景（先清空旧数据，再导入新数据）
  ///   - TRUNCATE 是 DDL 操作，会隐式提交事务
  ///
  /// **注意**：此操作会清空所有版本的数据，不可逆。
  void truncateAll();
}
