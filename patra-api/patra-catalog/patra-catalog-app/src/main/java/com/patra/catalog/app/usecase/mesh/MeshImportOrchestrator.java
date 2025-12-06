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
import com.patra.catalog.domain.port.parser.XmlParserPort;
import com.patra.catalog.domain.port.repository.MeshDescriptorRepository;
import com.patra.catalog.domain.port.repository.MeshQualifierRepository;
import com.patra.catalog.domain.port.source.MeshSourceFilePort;
import com.patra.common.error.ApplicationException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
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

  private final XmlParserPort xmlParserPort;
  private final MeshQualifierRepository qualifierRepository;
  private final MeshDescriptorRepository descriptorRepository;
  private final MeshDescriptorBatchPort meshDescriptorBatchPort;
  private final MeshSourceFilePort meshSourceFilePort;

  /// 导入 MeSH 限定词。
  ///
  /// 从远程 URL 下载 XML 文件，解析并批量保存。
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

    // 2. 从远程下载文件
    Path localFile =
        meshSourceFilePort.fetchQualifierFile(command.meshVersion(), URI.create(command.url()));
    log.info("限定词文件已就绪：{}", localFile);

    try {
      // 3. 解析 XML 并批量保存（使用 Path 重载方法，由 Infra 层负责文件 I/O）
      List<MeshQualifierAggregate> qualifiers =
          xmlParserPort.parseQualifiers(localFile, command.meshVersion()).toList();
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
    } finally {
      // 无论成功或失败，都清理临时文件
      cleanupTempFile(localFile);
    }
  }

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
  public MeshDescriptorImportResult importDescriptors(MeshDescriptorImportCommand command) {
    log.info("启动 MeSH 主题词导入，URL：{}，版本：{}", command.url(), command.meshVersion());

    // 1. 检查数据是否已存在
    if (descriptorRepository.hasAnyData()) {
      throw new DataAlreadyExistsException("MeSH Descriptor");
    }

    // 2. 从远程下载文件
    Path localFile =
        meshSourceFilePort.fetchDescriptorFile(command.meshVersion(), URI.create(command.url()));
    log.info("主题词文件已就绪：{}", localFile);

    // 3. 启动批处理导入
    // 注意：文件由 Job Listener（MeshImportJobExecutionListener）在 Job 结束后清理，
    //      仅当 Job 启动失败时才在此处清理。
    try {
      MeshImportParams params =
          MeshImportParams.withTempFile(localFile.toString(), command.meshVersion());
      Long executionId = meshDescriptorBatchPort.launchImport(params);

      return MeshDescriptorImportResult.success(
          executionId, command.url(), localFile.toString(), command.meshVersion());
    } catch (ApplicationException e) {
      cleanupTempFile(localFile);
      throw e;
    } catch (RuntimeException e) {
      cleanupTempFile(localFile);
      throw new ApplicationException(
          CatalogErrorCode.CAT_1002, "MeSH 主题词导入失败: " + e.getMessage(), e);
    } catch (Exception e) {
      cleanupTempFile(localFile);
      throw new ApplicationException(
          CatalogErrorCode.CAT_1002, "MeSH 主题词导入时发生意外错误: " + e.getMessage(), e);
    }
  }

  /// 清理临时文件。
  private void cleanupTempFile(Path file) {
    try {
      Files.deleteIfExists(file);
      log.info("已清理临时文件：{}", file);
    } catch (IOException e) {
      log.warn("清理临时文件失败：{}，原因：{}", file, e.getMessage());
    }
  }
}
