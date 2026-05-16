package dev.linqibin.patra.catalog.app.usecase.mesh;

import dev.linqibin.commons.cqrs.CommandHandler;
import dev.linqibin.commons.error.ApplicationException;
import dev.linqibin.patra.catalog.api.error.CatalogErrorCode;
import dev.linqibin.patra.catalog.app.usecase.mesh.command.MeshDescriptorImportCommand;
import dev.linqibin.patra.catalog.app.usecase.mesh.dto.MeshDescriptorImportResult;
import dev.linqibin.patra.catalog.domain.exception.DataAlreadyExistsException;
import dev.linqibin.patra.catalog.domain.model.vo.mesh.MeshImportParams;
import dev.linqibin.patra.catalog.domain.port.batch.MeshBatchPort;
import dev.linqibin.patra.catalog.domain.port.repository.MeshDescriptorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// MeSH 主题词导入处理器。
///
/// **职责**：
///
/// - 编排 MeSH 主题词导入流程
/// - 委派具体任务给领域端口
///
/// **临时文件下载特性**：
///
/// - ItemReader 在 open() 时通过 FileDownloadPort 下载文件到临时目录
/// - 传递 downloadUrl 给 Job，由 ItemReader 负责下载到临时文件
///
/// **设计说明**：
///
/// 导入操作设计为「一次性初始化」语义：
///
/// - 不支持增量或覆盖模式
/// - 如果表中已有数据，直接抛出 `DataAlreadyExistsException`
/// - 如需重新导入，必须先手动清空数据库
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class MeshDescriptorImportHandler
    implements CommandHandler<MeshDescriptorImportCommand, MeshDescriptorImportResult> {

  private final MeshDescriptorRepository descriptorRepository;
  private final MeshBatchPort meshBatchPort;

  /// 导入 MeSH 主题词。
  ///
  /// 大数据量（约 35,000 条），使用批处理进行导入。
  ///
  /// **前置条件**：
  ///
  /// - 数据库中不存在任何主题词数据
  ///
  /// **异常情况**：
  ///
  /// - 如果数据库中已有数据，抛出 `DataAlreadyExistsException`
  ///
  /// @param command 导入命令（包含文件 URL 和版本）
  /// @return 导入结果
  /// @throws DataAlreadyExistsException 当表中已有数据时
  @Override
  public MeshDescriptorImportResult handle(MeshDescriptorImportCommand command) {
    log.info("启动 MeSH 主题词导入，URL：{}，版本：{}", command.url(), command.meshVersion());

    // 1. 检查数据是否已存在
    if (descriptorRepository.hasAnyData()) {
      throw new DataAlreadyExistsException("MeSH Descriptor");
    }

    // 2. 启动批处理导入（传递 downloadUrl，由 ItemReader 负责下载到临时文件）
    try {
      MeshImportParams params =
          MeshImportParams.withDownloadUrl(command.url(), command.meshVersion());
      Long executionId = meshBatchPort.launchDescriptorImport(params);

      log.info("MeSH 主题词导入任务已启动，executionId：{}", executionId);
      return MeshDescriptorImportResult.success(executionId, command.url(), command.meshVersion());

    } catch (ApplicationException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new ApplicationException(
          CatalogErrorCode.CAT_1002, "MeSH 主题词导入失败: " + e.getMessage(), e);
    } catch (Exception e) {
      throw new ApplicationException(
          CatalogErrorCode.CAT_1002, "MeSH 主题词导入时发生意外错误: " + e.getMessage(), e);
    }
    // 无需清理临时文件，ItemReader 在 close() 时自动删除临时文件
  }
}
