package com.patra.catalog.app.usecase.mesh;

import com.patra.catalog.api.error.CatalogErrorCode;
import com.patra.catalog.app.usecase.mesh.command.MeshScrImportCommand;
import com.patra.catalog.app.usecase.mesh.dto.MeshScrImportResult;
import com.patra.catalog.domain.exception.DataAlreadyExistsException;
import com.patra.catalog.domain.model.vo.mesh.MeshImportParams;
import com.patra.catalog.domain.port.batch.MeshScrBatchPort;
import com.patra.catalog.domain.port.repository.MeshScrRepository;
import com.patra.common.cqrs.CommandHandler;
import com.patra.common.error.ApplicationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// MeSH SCR 导入处理器。
///
/// **职责**：
///
/// - 编排 MeSH 补充概念记录（SCR）导入流程
/// - 委派具体任务给领域端口
///
/// **流式处理特性**：
///
/// - 无磁盘落盘，ItemReader 在 open() 时建立 HTTP 连接
/// - 传递 downloadUrl 给 Job，由 ItemReader 负责流式下载
///
/// **设计说明**：
///
/// 导入操作设计为「一次性初始化」语义：
///
/// - 不支持增量或覆盖模式
/// - 如果表中已有数据，直接抛出 `DataAlreadyExistsException`
/// - 如需重新导入，必须先手动清空数据库
///
/// **数据规模**：
///
/// SCR 约 350,000 条记录，是 Descriptor 的 10 倍，采用流式处理避免内存溢出。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class MeshScrImportHandler
    implements CommandHandler<MeshScrImportCommand, MeshScrImportResult> {

  private final MeshScrRepository scrRepository;
  private final MeshScrBatchPort meshScrBatchPort;

  /// 导入 MeSH SCR。
  ///
  /// 大数据量（约 350,000 条），使用批处理进行导入。
  ///
  /// **前置条件**：
  ///
  /// - 数据库中不存在任何 SCR 数据
  ///
  /// **异常情况**：
  ///
  /// - 如果数据库中已有数据，抛出 `DataAlreadyExistsException`
  ///
  /// @param command 导入命令（包含文件 URL 和版本）
  /// @return 导入结果
  /// @throws DataAlreadyExistsException 当表中已有数据时
  @Override
  public MeshScrImportResult handle(MeshScrImportCommand command) {
    log.info("启动 MeSH SCR 导入，URL：{}，版本：{}", command.url(), command.meshVersion());

    // 1. 检查数据是否已存在
    if (scrRepository.hasAnyData()) {
      throw new DataAlreadyExistsException("MeSH SCR");
    }

    // 2. 启动批处理导入（传递 downloadUrl，由 ItemReader 负责流式下载）
    try {
      MeshImportParams params =
          MeshImportParams.withDownloadUrl(command.url(), command.meshVersion());
      Long executionId = meshScrBatchPort.launchImport(params);

      log.info("MeSH SCR 导入任务已启动，executionId：{}", executionId);
      return MeshScrImportResult.success(executionId, command.url(), command.meshVersion());

    } catch (ApplicationException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new ApplicationException(
          CatalogErrorCode.CAT_1002, "MeSH SCR 导入失败: " + e.getMessage(), e);
    } catch (Exception e) {
      throw new ApplicationException(
          CatalogErrorCode.CAT_1002, "MeSH SCR 导入时发生意外错误: " + e.getMessage(), e);
    }
    // 无需清理临时文件，ItemReader 在 close() 时自动关闭 HTTP 连接
  }
}
