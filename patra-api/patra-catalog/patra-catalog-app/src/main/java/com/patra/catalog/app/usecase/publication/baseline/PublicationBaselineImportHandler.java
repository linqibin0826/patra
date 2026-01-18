package com.patra.catalog.app.usecase.publication.baseline;

import com.patra.catalog.api.error.CatalogErrorCode;
import com.patra.catalog.app.usecase.publication.baseline.command.PublicationBaselineImportCommand;
import com.patra.catalog.app.usecase.publication.baseline.dto.PublicationBaselineImportResult;
import com.patra.catalog.domain.model.vo.publication.PublicationImportParams;
import com.patra.catalog.domain.port.batch.PublicationBatchPort;
import com.patra.common.cqrs.CommandHandler;
import com.patra.common.error.ApplicationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// PubMed Baseline 文献导入处理器。
///
/// **职责**：
///
/// - 编排 PubMed Baseline 文献导入流程
/// - 委派具体任务给领域端口
///
/// **单文件模式**：
///
/// 每次处理只导入一个 XML 文件（由 `command.fileIndex` 指定）。
/// 这种设计支持：
///
/// - 测试环境只导入 1 个文件
/// - 生产环境通过 XXL-Job 循环调度批量导入 1274 个文件
/// - 断点续传（从指定文件继续）
///
/// **流式处理特性**：
///
/// - 无磁盘落盘，ItemReader 在 open() 时建立 HTTP 连接
/// - 传递 downloadUrl 给 Job，由 ItemReader 负责流式下载
///
/// **设计说明**：
///
/// 与 MeSH 导入不同，文献导入支持增量模式：
///
/// - 不检查表是否为空
/// - 处理器阶段自动去重（existsByPmid）
/// - 同一文件可安全重复执行
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class PublicationBaselineImportHandler
    implements CommandHandler<PublicationBaselineImportCommand, PublicationBaselineImportResult> {

  private final PublicationBatchPort publicationBatchPort;

  /// 导入 PubMed Baseline 文献。
  ///
  /// 大数据量（单文件约 30,000 条），使用批处理进行导入。
  ///
  /// **增量模式**：
  ///
  /// - 不检查表是否为空，支持增量导入
  /// - Processor 阶段通过 existsByPmid 去重，跳过已存在的记录
  /// - 同一文件可安全重复执行
  ///
  /// @param command 导入命令（包含 baseUrl 和 fileIndex）
  /// @return 导入结果
  @Override
  public PublicationBaselineImportResult handle(PublicationBaselineImportCommand command) {
    log.info(
        "启动 PubMed Baseline 导入，baseUrl：{}，fileIndex：{}", command.baseUrl(), command.fileIndex());

    try {
      // 构建导入参数
      PublicationImportParams params =
          PublicationImportParams.of(command.baseUrl(), command.fileIndex());

      // 启动批处理导入
      Long executionId = publicationBatchPort.launchBaselineImport(params);

      log.info(
          "PubMed Baseline 导入任务已启动，executionId：{}，fileName：{}", executionId, params.getFileName());

      return PublicationBaselineImportResult.success(
          executionId, command.baseUrl(), command.fileIndex(), params.getFileName());

    } catch (ApplicationException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new ApplicationException(
          CatalogErrorCode.CAT_1501, "PubMed Baseline 导入失败: " + e.getMessage(), e);
    } catch (Exception e) {
      throw new ApplicationException(
          CatalogErrorCode.CAT_1501, "PubMed Baseline 导入时发生意外错误: " + e.getMessage(), e);
    }
  }
}
