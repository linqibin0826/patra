package com.patra.catalog.app.usecase.mesh;

import com.patra.catalog.app.usecase.mesh.command.MeshDescriptorImportCommand;
import com.patra.catalog.app.usecase.mesh.command.MeshQualifierImportCommand;
import com.patra.catalog.app.usecase.mesh.dto.MeshDescriptorImportResult;
import com.patra.catalog.app.usecase.mesh.dto.MeshQualifierImportResult;
import com.patra.catalog.domain.model.aggregate.MeshQualifierAggregate;
import com.patra.catalog.domain.model.enums.MeshDescriptorImportMode;
import com.patra.catalog.domain.model.vo.mesh.MeshImportParams;
import com.patra.catalog.domain.port.FileDownloadPort;
import com.patra.catalog.domain.port.MeshDescriptorBatchPort;
import com.patra.catalog.domain.port.MeshDescriptorRepository;
import com.patra.catalog.domain.port.MeshQualifierRepository;
import com.patra.catalog.domain.port.XmlParserPort;
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
  private final FileDownloadPort fileDownloadPort;

  /// 导入 MeSH 限定词。
  ///
  /// 从远程 URL 下载 XML 文件，清空现有数据，解析并批量保存。
  ///
  /// **导入模式**：
  ///
  /// 限定词仅支持 TRUNCATE_REIMPORT 模式，每次导入前会清空所有现有数据。
  ///
  /// **注意**：TRUNCATE 是 DDL 操作，会隐式提交事务，无法回滚。
  ///
  /// @param command 导入命令（包含 URL 和版本号）
  /// @return 导入结果摘要
  @Override
  @Transactional
  public MeshQualifierImportResult importQualifiers(MeshQualifierImportCommand command) {
    log.info("启动 MeSH 限定词导入，URL：{}，版本：{}", command.url(), command.meshVersion());

    // 1. 下载文件到临时目录
    Path localFile = fileDownloadPort.downloadToTemp(URI.create(command.url()));
    log.info("限定词文件已下载到：{}", localFile);

    try {
      // 2. 清空现有数据（TRUNCATE_REIMPORT 模式）
      qualifierRepository.truncateAll();
      log.info("已清空所有限定词旧数据");

      // 3. 解析 XML 并批量保存（使用 Path 重载方法，由 Infra 层负责文件 I/O）
      List<MeshQualifierAggregate> qualifiers =
          xmlParserPort.parseQualifiers(localFile, command.meshVersion()).toList();
      qualifierRepository.saveBatch(qualifiers);

      log.info("MeSH 限定词导入完成，数量：{}", qualifiers.size());
      return MeshQualifierImportResult.success(command.url(), command.meshVersion(), qualifiers.size());
    } catch (Exception e) {
      throw (RuntimeException) e;
    } finally {
      // 无论成功或失败，都清理临时文件
      cleanupTempFile(localFile);
    }
  }

  /// 导入 MeSH 主题词。
  ///
  /// 大数据量（约 35,000 条），使用批处理进行导入。
  ///
  /// **导入模式**：
  ///
  /// - `INCREMENTAL`：增量导入，幂等执行，支持断点续传
  /// - `TRUNCATE_REIMPORT`：清空重导入，先 TRUNCATE 所有表再重新导入
  ///
  /// **注意**（TRUNCATE_REIMPORT 模式）：
  ///
  /// - TRUNCATE 是 DDL 操作，会隐式提交事务，无法回滚
  /// - 清空操作会删除所有版本的数据，不仅仅是指定版本
  /// - 如果清空成功但导入失败，需要重新执行此方法
  ///
  /// @param command 导入命令（包含文件路径、版本、模式）
  /// @return 导入结果
  @Override
  public MeshDescriptorImportResult importDescriptors(MeshDescriptorImportCommand command) {
    log.info(
        "启动 MeSH 主题词导入，URL：{}，版本：{}，模式：{}",
        command.url(),
        command.meshVersion(),
        command.mode());

    // 1. 下载文件到临时目录
    Path localFile = fileDownloadPort.downloadToTemp(URI.create(command.url()));
    log.info("文件已下载到：{}", localFile);

    // 2. 清空数据（如果是 TRUNCATE 模式）
    if (command.mode() == MeshDescriptorImportMode.TRUNCATE_REIMPORT) {
      descriptorRepository.truncateAll();
      log.info("已清空所有旧数据");
    }

    // 3. 启动批处理导入（标记为临时文件，Job 完成后自动清理）
    try {
      boolean forceNewInstance = (command.mode() == MeshDescriptorImportMode.TRUNCATE_REIMPORT);
      MeshImportParams params =
          new MeshImportParams(localFile.toString(), command.meshVersion(), forceNewInstance, true);
      Long executionId = meshDescriptorBatchPort.launchImport(params);

      return MeshDescriptorImportResult.success(
          executionId, command.url(), localFile.toString(), command.meshVersion(), command.mode());
    } catch (Exception e) {
      // Job 启动失败，清理临时文件
      cleanupTempFile(localFile);
      throw e;
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
