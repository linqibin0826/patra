package com.patra.catalog.app.usecase.mesh;

import com.patra.catalog.api.error.CatalogErrorCode;
import com.patra.catalog.app.usecase.mesh.command.MeshDescriptorImportCommand;
import com.patra.catalog.app.usecase.mesh.command.MeshQualifierImportCommand;
import com.patra.catalog.app.usecase.mesh.dto.MeshDescriptorImportResult;
import com.patra.catalog.app.usecase.mesh.dto.MeshQualifierImportResult;
import com.patra.catalog.domain.exception.DataAlreadyExistsException;
import com.patra.catalog.domain.model.aggregate.MeshQualifierAggregate;
import com.patra.catalog.domain.model.vo.mesh.MeshImportParams;
import com.patra.catalog.domain.port.batch.MeshDescriptorBatchPort;
import com.patra.catalog.domain.port.parser.MeshQualifierParserPort;
import com.patra.catalog.domain.port.repository.MeshDescriptorRepository;
import com.patra.catalog.domain.port.repository.MeshQualifierRepository;
import com.patra.catalog.domain.port.source.StreamingDownloadPort;
import com.patra.catalog.domain.port.source.StreamingDownloadResult;
import com.patra.common.error.ApplicationException;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// MeSH 数据导入编排器。
///
/// **职责**：
///
/// - 编排 MeSH 数据导入流程
/// - 管理事务边界
/// - 委派具体任务给领域端口
///
/// **导入顺序**：
///
/// 1. 先导入 Qualifier（限定词，约 100 条，同步事务）
/// 2. 再导入 Descriptor（主题词，约 35,000 条，批处理）
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
@Service
@RequiredArgsConstructor
public class MeshImportOrchestrator implements MeshImportUseCase {

  private final StreamingDownloadPort streamingDownloadPort;
  private final MeshQualifierParserPort qualifierParserPort;
  private final MeshQualifierRepository qualifierRepository;
  private final MeshDescriptorRepository descriptorRepository;
  private final MeshDescriptorBatchPort meshDescriptorBatchPort;

  /// 导入 MeSH 限定词。
  ///
  /// 从远程 URL 流式下载并解析 XML，批量保存到数据库。
  ///
  /// **流式处理特性**：
  ///
  /// - 无磁盘落盘，HTTP 响应体直接传递给 Parser
  /// - 使用 try-with-resources 自动管理 HTTP 连接
  ///
  /// **前置条件**：
  ///
  /// - 数据库中不存在任何限定词数据
  ///
  /// **异常情况**：
  ///
  /// - 如果数据库中已有数据，抛出 `DataAlreadyExistsException`
  ///
  /// @param command 导入命令（包含 URL 和版本号）
  /// @return 导入结果摘要
  /// @throws DataAlreadyExistsException 当表中已有数据时
  @Override
  @Transactional
  public MeshQualifierImportResult importQualifiers(MeshQualifierImportCommand command) {
    log.info("启动 MeSH 限定词导入，URL：{}，版本：{}", command.url(), command.meshVersion());

    // 1. 检查数据是否已存在
    if (qualifierRepository.hasAnyData()) {
      throw new DataAlreadyExistsException("MeSH Qualifier");
    }

    // 2. 流式下载并解析（无磁盘落盘）
    try (StreamingDownloadResult downloadResult =
        streamingDownloadPort.download(URI.create(command.url()))) {

      log.info("HTTP 连接建立成功，开始流式解析");

      // 3. 解析 XML 并设置版本号
      List<MeshQualifierAggregate> qualifiers =
          qualifierParserPort
              .parse(downloadResult.inputStream())
              .map(q -> q.withMeshVersion(command.meshVersion()))
              .toList();

      // 4. 批量保存
      qualifierRepository.saveBatch(qualifiers);

      log.info("MeSH 限定词导入完成，数量：{}", qualifiers.size());
      return MeshQualifierImportResult.success(
          command.url(), command.meshVersion(), qualifiers.size());

    } catch (ApplicationException e) {
      // 已经是 ApplicationException，直接重新抛出
      throw e;
    } catch (RuntimeException e) {
      // 包装其他运行时异常
      throw new ApplicationException(
          CatalogErrorCode.CAT_1001, "MeSH 限定词导入失败: " + e.getMessage(), e);
    } catch (Exception e) {
      // 包装检查异常
      throw new ApplicationException(
          CatalogErrorCode.CAT_1001, "MeSH 限定词导入时发生意外错误: " + e.getMessage(), e);
    }
    // 无需 finally 清理临时文件，try-with-resources 自动关闭 HTTP 连接
  }

  /// 导入 MeSH 主题词。
  ///
  /// 大数据量（约 35,000 条），使用批处理进行导入。
  ///
  /// **流式处理特性**：
  ///
  /// - 无磁盘落盘，ItemReader 在 open() 时建立 HTTP 连接
  /// - 传递 downloadUrl 给 Job，由 ItemReader 负责流式下载
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
  public MeshDescriptorImportResult importDescriptors(MeshDescriptorImportCommand command) {
    log.info("启动 MeSH 主题词导入，URL：{}，版本：{}", command.url(), command.meshVersion());

    // 1. 检查数据是否已存在
    if (descriptorRepository.hasAnyData()) {
      throw new DataAlreadyExistsException("MeSH Descriptor");
    }

    // 2. 启动批处理导入（传递 downloadUrl，由 ItemReader 负责流式下载）
    try {
      MeshImportParams params =
          MeshImportParams.withDownloadUrl(command.url(), command.meshVersion());
      Long executionId = meshDescriptorBatchPort.launchImport(params);

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
    // 无需清理临时文件，ItemReader 在 close() 时自动关闭 HTTP 连接
  }
}
