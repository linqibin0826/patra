package com.patra.catalog.app.usecase.meshimport.strategy;

import com.patra.catalog.domain.model.aggregate.MeshImportAggregate;
import com.patra.catalog.domain.model.enums.MeshDataType;
import java.io.File;

/// MeSH 数据导入策略接口。
///
/// 职责：
///
/// - 定义单一数据类型的导入行为
///   - 解析 XML 文件
///   - 批量保存到数据库
///   - 更新聚合根的表进度
///
/// **策略模式**：
///
/// - 每个实现类负责一种数据类型（Qualifier、Descriptor、TreeNumber、EntryTerm、Concept）
///   - 由 {@link com.patra.catalog.app.usecase.meshimport.MeshImportOrchestrator} 编排调用
///   - 符合开闭原则：新增数据类型只需新增实现类，无需修改 Orchestrator
///
/// **实现要求**：
///
/// - 必须是 Spring Bean（@Component）
///   - 实现 {@link #importData} 方法
///   - 实现 {@link #getDataType} 返回对应的枚举值
///
/// **设计原则**：
///
/// - 单一职责：每个实现类只负责一种数据类型
///   - 依赖倒置：Orchestrator 依赖接口，不依赖具体实现
///   - 开闭原则：扩展新数据类型不需要修改已有代码
///
/// @author linqibin
/// @since 0.1.0
public interface MeshDataImporter {

  /// 导入数据。
  ///
  /// 从 XML 文件解析并批量保存到数据库。
  ///
  /// @param xmlFile XML 文件
  /// @param aggregate 导入任务聚合根（用于更新表进度）
  /// @return 实际导入的记录数
  /// @throws Exception 如果解析或保存失败
  int importData(File xmlFile, MeshImportAggregate aggregate) throws Exception;

  /// 获取数据类型。
  ///
  /// 用于 Orchestrator 识别和调用对应的策略。
  ///
  /// @return 数据类型枚举
  MeshDataType getDataType();
}
