package com.patra.catalog.app.usecase.venue.initialize;

import com.patra.catalog.api.error.CatalogErrorCode;
import com.patra.catalog.app.usecase.venue.initialize.command.VenueInitializeCommand;
import com.patra.catalog.app.usecase.venue.initialize.dto.VenueInitializeResult;
import com.patra.catalog.domain.exception.DataAlreadyExistsException;
import com.patra.catalog.domain.model.vo.venue.OpenAlexManifest;
import com.patra.catalog.domain.model.vo.venue.VenueInitializeParams;
import com.patra.catalog.domain.port.batch.VenueInitializeBatchPort;
import com.patra.catalog.domain.port.repository.VenueRepository;
import com.patra.catalog.domain.port.source.VenueSourceFilePort;
import com.patra.common.cqrs.CommandHandler;
import com.patra.common.error.ApplicationException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
/// 2. 流式获取 manifest（获取分区文件 URL 列表）
/// 3. 传递分区 URL 列表给 Spring Batch Job
/// 4. ItemReader 按需流式下载每个分区文件
///
/// **流式处理特性**：
///
/// - 无磁盘落盘，Manifest 直接从 HTTP 响应解析
/// - 分区文件由 ItemReader 按需下载，切换文件时关闭当前 HTTP 连接
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
public class VenueInitializeHandler
    implements CommandHandler<VenueInitializeCommand, VenueInitializeResult> {

  private final VenueSourceFilePort venueSourceFilePort;
  private final VenueInitializeBatchPort venueImportBatchPort;
  private final VenueRepository venueRepository;

  /// 执行 OpenAlex Venue 导入。
  ///
  /// **流式处理特性**：
  ///
  /// - Manifest 直接从 HTTP 响应解析，无磁盘落盘
  /// - 分区 URL 列表传递给 Job，由 ItemReader 按需流式下载
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
  public VenueInitializeResult handle(VenueInitializeCommand command) {
    log.info("启动 OpenAlex Venue 导入");

    // 1. 检查数据是否已存在
    if (venueRepository.hasAnyData()) {
      throw new DataAlreadyExistsException("Venue");
    }

    try {
      // 2. 流式获取 manifest（无磁盘落盘）
      OpenAlexManifest manifest = venueSourceFilePort.fetchManifest();
      log.info(
          "获取 manifest 成功，分区数：{}，总记录数：{}", manifest.entries().size(), manifest.totalRecordCount());

      // 3. 提取分区 HTTP URL 列表（由 ItemReader 按需下载）
      List<String> partitionUrls = manifest.getAllHttpPaths();
      log.info("准备启动导入任务，分区 URL 数量：{}", partitionUrls.size());

      // 4. 启动批处理导入（传递 URL 列表，由 ItemReader 负责流式下载）
      VenueInitializeParams params = VenueInitializeParams.of(partitionUrls);
      Long executionId = venueImportBatchPort.launchImport(params);

      log.info("OpenAlex Venue 导入任务已启动，executionId：{}", executionId);
      return VenueInitializeResult.success(
          executionId, manifest.entries().size(), manifest.totalRecordCount());

    } catch (ApplicationException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new ApplicationException(
          CatalogErrorCode.CAT_1301, "OpenAlex Venue 导入失败: " + e.getMessage(), e);
    } catch (Exception e) {
      throw new ApplicationException(
          CatalogErrorCode.CAT_1301, "OpenAlex Venue 导入时发生意外错误: " + e.getMessage(), e);
    }
    // 无需清理临时文件，ItemReader 使用流式下载
  }
}
