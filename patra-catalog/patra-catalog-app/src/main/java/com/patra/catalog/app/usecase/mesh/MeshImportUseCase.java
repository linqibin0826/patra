package com.patra.catalog.app.usecase.mesh;

import com.patra.catalog.app.usecase.mesh.command.MeshImportCommand;
import com.patra.catalog.app.usecase.mesh.dto.MeshImportResult;

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
}
