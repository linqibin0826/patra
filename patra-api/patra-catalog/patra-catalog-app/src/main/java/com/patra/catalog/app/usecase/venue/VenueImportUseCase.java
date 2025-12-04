package com.patra.catalog.app.usecase.venue;

import com.patra.catalog.app.usecase.venue.command.VenueImportCommand;
import com.patra.catalog.app.usecase.venue.dto.VenueImportResult;

/// OpenAlex Venue 导入用例接口，定义调度入口契约。
///
/// 用于 Adapter 层依赖，实现依赖倒置。
///
/// **导入流程**：
///
/// 1. 检查数据存在性（表中已有数据时拒绝导入）
/// 2. 获取 OpenAlex Sources manifest（获取分区文件列表）
/// 3. 下载所有分区文件到本地临时目录（支持 MinIO 缓存）
/// 4. 启动 Spring Batch Job 进行批量导入（纯 INSERT）
///
/// **一次性初始化语义**：
///
/// - 表中已有数据时抛出 `DataAlreadyExistsException`
/// - 需重新导入时由用户手动清空数据库后再执行
/// - 写入策略为纯 INSERT（非 Upsert）
///
/// **与 MeshImportUseCase 的差异**：
///
/// - 无需传入 URL（从 manifest 动态获取分区列表）
/// - 无需版本号（OpenAlex 使用 updated_date 分区）
///
/// @author linqibin
/// @since 0.1.0
public interface VenueImportUseCase {

  /// 执行 OpenAlex Venue 导入。
  ///
  /// @param command 导入命令
  /// @return 导入结果摘要
  VenueImportResult importVenues(VenueImportCommand command);
}
