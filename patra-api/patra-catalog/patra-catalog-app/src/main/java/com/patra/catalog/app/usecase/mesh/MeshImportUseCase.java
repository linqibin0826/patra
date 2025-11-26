package com.patra.catalog.app.usecase.mesh;

import com.patra.catalog.app.usecase.mesh.command.MeshImportCommand;
import com.patra.catalog.app.usecase.mesh.command.MeshQualifierImportCommand;
import com.patra.catalog.app.usecase.mesh.dto.MeshImportResult;
import com.patra.catalog.app.usecase.mesh.dto.MeshQualifierImportResult;

/// MeSH 导入用例接口，定义调度入口契约。
///
/// 用于 Adapter 层依赖，实现依赖倒置。
///
/// @author linqibin
/// @since 0.1.0
public interface MeshImportUseCase {

  /// 执行 MeSH 主题词导入。
  ///
  /// @param command 导入命令
  /// @return 导入结果摘要
  MeshImportResult importDescriptors(MeshImportCommand command);

  /// 执行 MeSH 限定词导入。
  ///
  /// 限定词数据量小（约 80 条），不使用 Spring Batch，直接在事务内批量保存。
  /// 每次导入前会清空所有现有限定词数据（TRUNCATE_REIMPORT 模式）。
  ///
  /// @param command 导入命令
  /// @return 导入结果摘要
  MeshQualifierImportResult importQualifiers(MeshQualifierImportCommand command);
}
