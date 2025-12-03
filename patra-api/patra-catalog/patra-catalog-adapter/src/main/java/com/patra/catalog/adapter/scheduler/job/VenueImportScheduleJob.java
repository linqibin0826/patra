package com.patra.catalog.adapter.scheduler.job;

import cn.hutool.core.text.CharSequenceUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.catalog.adapter.scheduler.param.VenueImportJobParam;
import com.patra.catalog.app.usecase.venue.VenueImportUseCase;
import com.patra.catalog.app.usecase.venue.command.VenueImportCommand;
import com.patra.catalog.app.usecase.venue.dto.VenueImportResult;
import com.patra.catalog.domain.exception.CatalogScheduleParameterException;
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
/// - 协议转换：将 XXL-Job JSON 参数转换为 Application 层 Command
/// - 日志记录：记录任务开始、结束和错误信息
/// - 结果报告：向 XXL-Job 控制台报告执行状态
///
/// **与 MeSH 导入的差异**：
///
/// - 无需传入 URL（从 OpenAlex S3 Manifest 动态获取分区文件列表）
/// - 无需版本号（OpenAlex 使用 updated_date 分区管理版本）
/// - 写入策略为 Upsert（更新或插入）
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class VenueImportScheduleJob {

  private final ObjectMapper objectMapper;
  private final VenueImportUseCase venueImportUseCase;

  /// 执行 OpenAlex Venue 导入任务。
  ///
  /// **JobHandler 名称**: `venueImportJob`
  ///
  /// **参数格式**（JSON，可选）：
  ///
  /// ```json
  /// {
  ///   "mode": "INCREMENTAL"
  /// }
  /// ```
  ///
  /// 或者使用空参数（默认使用 INCREMENTAL 模式）：
  ///
  /// ```json
  /// {}
  /// ```
  ///
  /// **导入模式**：
  ///
  /// - `INCREMENTAL`：增量导入（Upsert），支持断点续传
  /// - `TRUNCATE_REIMPORT`：清空重导入，先清空所有数据再重新导入
  @XxlJob("venueImportJob")
  public void executeVenueImport() {
    String rawParam = XxlJobHelper.getJobParam();
    log.info("OpenAlex Venue 导入任务已触发，jobId [{}]，参数：{}", XxlJobHelper.getJobId(), rawParam);

    try {
      VenueImportCommand command = parseJobParam(rawParam);
      log.debug("已解析 Venue 导入命令：模式 [{}]", command.mode());

      VenueImportResult result = venueImportUseCase.importVenues(command);
      handleSuccess(result.message());

    } catch (CatalogScheduleParameterException ex) {
      handleParameterError(ex);
      // 已通过 handleFail 报告失败，不再抛出异常
    } catch (Exception ex) {
      handleExecutionError(ex);
      // 已通过 handleFail 报告失败，不再抛出异常
    }
  }

  /// 解析 JSON 参数并转换为 Command。
  ///
  /// 只负责 JSON 反序列化（协议转换），参数验证委托给 Command。
  /// 支持空参数或空 JSON 对象，此时使用默认模式（INCREMENTAL）。
  ///
  /// @param rawParam 原始 JSON 参数（可为空）
  /// @return 导入命令
  /// @throws CatalogScheduleParameterException 当参数无法解析或验证失败时
  private VenueImportCommand parseJobParam(String rawParam) {
    // 支持空参数，使用默认模式
    if (CharSequenceUtil.isBlank(rawParam)) {
      log.debug("未提供参数，使用默认模式 INCREMENTAL");
      return VenueImportCommand.incremental();
    }

    VenueImportJobParam param;
    try {
      param = objectMapper.readValue(rawParam, VenueImportJobParam.class);
    } catch (Exception ex) {
      throw new CatalogScheduleParameterException("Venue 导入参数解析失败：" + ex.getMessage(), ex);
    }

    // 委托给 Command 进行参数验证和枚举转换
    return VenueImportCommand.of(param.modeOrDefault());
  }

  /// 处理参数错误。
  private void handleParameterError(CatalogScheduleParameterException ex) {
    log.warn("Venue 导入任务参数错误：{}", ex.getMessage());
    XxlJobHelper.handleFail("参数错误：" + ex.getMessage());
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
