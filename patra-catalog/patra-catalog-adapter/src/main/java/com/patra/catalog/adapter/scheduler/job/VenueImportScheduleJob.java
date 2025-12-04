package com.patra.catalog.adapter.scheduler.job;

import com.patra.catalog.app.usecase.venue.VenueImportUseCase;
import com.patra.catalog.app.usecase.venue.command.VenueImportCommand;
import com.patra.catalog.app.usecase.venue.dto.VenueImportResult;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// OpenAlex Venue 数据导入定时任务。
///
/// 通过 XXL-Job 控制台手动触发，执行 OpenAlex Venue（期刊）数据的批量导入。
///
/// **JobHandler 名称**: `venueImportJob`
///
/// **职责**：
///
/// - 协议转换：将 XXL-Job 请求转换为 Application 层 Command
/// - 日志记录：记录任务开始、结束和错误信息
/// - 结果报告：向 XXL-Job 控制台报告执行状态
///
/// **与 MeSH 导入的差异**：
///
/// - 无需传入 URL（从 OpenAlex S3 Manifest 动态获取分区文件列表）
/// - 无需版本号（OpenAlex 使用 updated_date 分区管理版本）
///
/// **导入策略**：
///
/// 纯 INSERT 策略，用于一次性数据初始化。如果表中已有数据，导入会失败。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class VenueImportScheduleJob {

  private final VenueImportUseCase venueImportUseCase;

  /// 执行 OpenAlex Venue 导入任务。
  ///
  /// **JobHandler 名称**: `venueImportJob`
  ///
  /// **参数**：无需参数，直接触发即可。
  @XxlJob("venueImportJob")
  public void executeVenueImport() {
    log.info("OpenAlex Venue 导入任务已触发，jobId [{}]", XxlJobHelper.getJobId());

    try {
      VenueImportCommand command = VenueImportCommand.create();
      VenueImportResult result = venueImportUseCase.importVenues(command);
      handleSuccess(result.message());

    } catch (Exception ex) {
      handleExecutionError(ex);
      // 已通过 handleFail 报告失败，不再抛出异常
    }
  }

  /// 处理执行错误。
  private void handleExecutionError(Exception ex) {
    log.error("Venue 导入任务执行失败：{}", ex.getMessage(), ex);
    XxlJobHelper.handleFail("执行失败：" + ex.getMessage());
  }

  /// 处理成功执行。
  ///
  /// @param message 成功消息
  private void handleSuccess(String message) {
    log.info(message);
    XxlJobHelper.handleSuccess(message);
  }
}
