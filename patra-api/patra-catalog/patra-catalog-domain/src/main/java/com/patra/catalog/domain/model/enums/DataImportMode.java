package com.patra.catalog.domain.model.enums;

/// 通用数据导入模式枚举。
///
/// 定义批量数据导入时的两种执行策略，适用于 MeSH 主题词导入、OpenAlex 期刊导入等场景：
///
/// - **INCREMENTAL**：增量导入，幂等执行，支持断点续传
/// - **TRUNCATE_REIMPORT**：清空重导入，先清空所有数据再重新导入
///
/// 约束：清空数据 ↔ 强制新实例（双向绑定）
///
/// - TRUNCATE_REIMPORT 模式下，必须先 TRUNCATE 所有表，然后强制创建新 Job 实例
/// - INCREMENTAL 模式下，不清空数据，相同参数复用 Job 实例（幂等）
///
/// @author linqibin
/// @since 0.1.0
public enum DataImportMode {

  /// 增量导入模式：幂等执行，支持断点续传。
  ///
  /// 特性：
  ///
  /// - 不清空现有数据
  /// - 相同参数复用 Job 实例（幂等）
  /// - 失败后可重新执行继续导入
  INCREMENTAL,

  /// 清空重导入模式：清空所有数据后重新导入。
  ///
  /// 特性：
  ///
  /// - 先 TRUNCATE 所有表
  /// - 强制创建新 Job 实例
  /// - 用于数据修正或版本更新的完整重导入
  ///
  /// **注意**：TRUNCATE 是 DDL 操作，会隐式提交事务，无法回滚
  TRUNCATE_REIMPORT
}
