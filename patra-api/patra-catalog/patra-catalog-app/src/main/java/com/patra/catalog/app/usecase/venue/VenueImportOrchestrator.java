package com.patra.catalog.app.usecase.venue;

import com.patra.catalog.api.error.CatalogErrorCode;
import com.patra.catalog.app.usecase.venue.command.VenueImportCommand;
import com.patra.catalog.app.usecase.venue.dto.VenueImportResult;
import com.patra.catalog.domain.model.enums.DataImportMode;
import com.patra.catalog.domain.model.vo.venue.OpenAlexManifest;
import com.patra.catalog.domain.model.vo.venue.VenueImportParams;
import com.patra.catalog.domain.port.VenueImportBatchPort;
import com.patra.catalog.domain.port.VenueRepository;
import com.patra.catalog.domain.port.VenueSourceFilePort;
import com.patra.common.error.ApplicationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/// OpenAlex Venue 数据导入编排器。
///
/// **职责**：
///
/// - 编排 OpenAlex Venue 数据导入流程
/// - 协调领域端口完成数据获取、清空、批量导入
/// - 不管理事务边界（批量导入由 Spring Batch 管理）
///
/// **导入流程**：
///
/// 1. 获取 manifest 文件（获取分区文件列表）
/// 2. 下载所有分区文件到本地临时目录
/// 3. 可选：清空现有数据（TRUNCATE_REIMPORT 模式）
/// 4. 启动 Spring Batch Job 进行批量导入
///
/// **与 MeshImportOrchestrator 的差异**：
///
/// - MeSH 使用单个大文件，Venue 使用 manifest + 多个分区文件
/// - MeSH 导入 Qualifier 和 Descriptor 分两步，Venue 一步完成
/// - Venue 写入策略为 Upsert（增量更新），MeSH 为纯新增
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Service
@RequiredArgsConstructor
public class VenueImportOrchestrator implements VenueImportUseCase {

  private final VenueSourceFilePort venueSourceFilePort;
  private final VenueImportBatchPort venueImportBatchPort;
  private final VenueRepository venueRepository;

  /// 执行 OpenAlex Venue 导入。
  ///
  /// **导入模式**：
  ///
  /// - `INCREMENTAL`：增量导入（Upsert），支持断点续传
  /// - `TRUNCATE_REIMPORT`：清空重导入，先 TRUNCATE 所有表再重新导入
  ///
  /// **注意**（TRUNCATE_REIMPORT 模式）：
  ///
  /// - TRUNCATE 是 DDL 操作，会隐式提交事务，无法回滚
  /// - 如果清空成功但导入失败，需要重新执行此方法
  ///
  /// @param command 导入命令（包含导入模式）
  /// @return 导入结果摘要
  @Override
  public VenueImportResult importVenues(VenueImportCommand command) {
    DataImportMode mode = command.mode();
    log.info("启动 OpenAlex Venue 导入，模式：{}", mode);

    List<Path> localFiles = null;
    try {
      // 1. 获取 manifest 文件
      OpenAlexManifest manifest = venueSourceFilePort.fetchManifest();
      log.info(
          "获取 manifest 成功，分区数：{}，总记录数：{}", manifest.entries().size(), manifest.totalRecordCount());

      // 2. 下载所有分区文件到本地临时目录
      localFiles = venueSourceFilePort.fetchAllPartitionFiles(manifest);
      log.info("所有分区文件已下载到本地，文件数：{}", localFiles.size());

      // 3. 清空数据（如果是 TRUNCATE 模式）
      if (mode == DataImportMode.TRUNCATE_REIMPORT) {
        venueRepository.truncateAll();
        log.info("已清空所有 Venue 旧数据");
      }

      // 4. 启动批处理导入
      boolean forceNewInstance = (mode == DataImportMode.TRUNCATE_REIMPORT);
      List<String> filePaths = localFiles.stream().map(Path::toString).toList();
      VenueImportParams params = VenueImportParams.withTempFiles(filePaths, forceNewInstance);
      Long executionId = venueImportBatchPort.launchImport(params);

      log.info("OpenAlex Venue 导入任务已启动，executionId：{}，模式：{}", executionId, mode);
      return VenueImportResult.success(
          executionId, manifest.entries().size(), manifest.totalRecordCount(), mode);

    } catch (ApplicationException e) {
      // 已经是 ApplicationException，清理后重新抛出
      cleanupTempFiles(localFiles);
      throw e;
    } catch (RuntimeException e) {
      // 包装其他运行时异常
      cleanupTempFiles(localFiles);
      throw new ApplicationException(
          CatalogErrorCode.CAT_1301, "OpenAlex Venue 导入失败: " + e.getMessage(), e);
    } catch (Exception e) {
      // 包装检查异常
      cleanupTempFiles(localFiles);
      throw new ApplicationException(
          CatalogErrorCode.CAT_1301, "OpenAlex Venue 导入时发生意外错误: " + e.getMessage(), e);
    }
    // 注意：成功启动 Job 后，临时文件由 Job Listener 在 Job 结束后清理
  }

  /// 清理临时文件。
  private void cleanupTempFiles(List<Path> files) {
    if (files == null || files.isEmpty()) {
      return;
    }
    for (Path file : files) {
      try {
        Files.deleteIfExists(file);
        log.debug("已清理临时文件：{}", file);
      } catch (IOException e) {
        log.warn("清理临时文件失败：{}，原因：{}", file, e.getMessage());
      }
    }
    log.info("已清理 {} 个临时文件", files.size());
  }
}
