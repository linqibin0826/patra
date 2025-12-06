package com.patra.catalog.app.usecase.venue;

import com.patra.catalog.api.error.CatalogErrorCode;
import com.patra.catalog.app.usecase.venue.command.VenueImportCommand;
import com.patra.catalog.app.usecase.venue.dto.VenueImportResult;
import com.patra.catalog.domain.exception.DataAlreadyExistsException;
import com.patra.catalog.domain.model.vo.venue.OpenAlexManifest;
import com.patra.catalog.domain.model.vo.venue.VenueImportParams;
import com.patra.catalog.domain.port.batch.VenueImportBatchPort;
import com.patra.catalog.domain.port.repository.VenueRepository;
import com.patra.catalog.domain.port.source.VenueSourceFilePort;
import com.patra.common.error.ApplicationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/// OpenAlex Venue 数据导入编排器。
///
/// **职责**：
///
/// - 编排 OpenAlex Venue 数据导入流程
/// - 协调领域端口完成数据获取、检查、批量导入
/// - 不管理事务边界（批量导入由 Spring Batch 管理）
///
/// **导入流程**：
///
/// 1. 检查数据库是否已有数据（如有则拒绝导入）
/// 2. 获取 manifest 文件（获取分区文件列表）
/// 3. 下载所有分区文件到本地临时目录
/// 4. 启动 Spring Batch Job 进行批量导入
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
public class VenueImportOrchestrator implements VenueImportUseCase {

  private final VenueSourceFilePort venueSourceFilePort;
  private final VenueImportBatchPort venueImportBatchPort;
  private final VenueRepository venueRepository;

  /// 执行 OpenAlex Venue 导入。
  ///
  /// **前置条件**：
  ///
  /// - 数据库中不存在任何 Venue 数据
  ///
  /// **异常情况**：
  ///
  /// - 如果数据库中已有数据，抛出 `DataAlreadyExistsException`
  ///
  /// @param command 导入命令
  /// @return 导入结果摘要
  /// @throws DataAlreadyExistsException 当表中已有数据时
  @Override
  public VenueImportResult importVenues(VenueImportCommand command) {
    log.info("启动 OpenAlex Venue 导入");

    // 1. 检查数据是否已存在
    if (venueRepository.hasAnyData()) {
      throw new DataAlreadyExistsException("Venue");
    }

    List<Path> localFiles = null;
    try {
      // 2. 获取 manifest 文件
      OpenAlexManifest manifest = venueSourceFilePort.fetchManifest();
      log.info(
          "获取 manifest 成功，分区数：{}，总记录数：{}", manifest.entries().size(), manifest.totalRecordCount());

      // 3. 下载所有分区文件到本地临时目录
      localFiles = fetchAllPartitionFiles(manifest);
      log.info("所有分区文件已下载到本地，文件数：{}", localFiles.size());

      // 4. 启动批处理导入
      List<String> filePaths = localFiles.stream().map(Path::toString).toList();
      VenueImportParams params = VenueImportParams.withTempFiles(filePaths);
      Long executionId = venueImportBatchPort.launchImport(params);

      log.info("OpenAlex Venue 导入任务已启动，executionId：{}", executionId);
      return VenueImportResult.success(
          executionId, manifest.entries().size(), manifest.totalRecordCount());

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

  /// 批量获取所有分区文件到本地临时目录。
  ///
  /// @param manifest 包含分区信息的 manifest
  /// @return 本地临时文件路径列表（与 manifest 中的顺序一致）
  private List<Path> fetchAllPartitionFiles(OpenAlexManifest manifest) {
    log.info("开始获取所有分区文件，共 {} 个", manifest.entries().size());

    List<Path> localFiles = new ArrayList<>();
    int total = manifest.entries().size();
    int current = 0;

    for (String relativePath : manifest.getRelativePaths()) {
      current++;
      if (current % 10 == 0 || current == total) {
        log.info("下载进度: {}/{}", current, total);
      }

      Path localFile = venueSourceFilePort.fetchPartitionFile(relativePath);
      localFiles.add(localFile);
    }

    log.info("所有分区文件获取完成，共 {} 个", localFiles.size());
    return localFiles;
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
