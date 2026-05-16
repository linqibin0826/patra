package dev.linqibin.patra.catalog.adapter.scheduler.job;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import dev.linqibin.commons.cqrs.CommandBus;
import dev.linqibin.patra.catalog.adapter.scheduler.config.RorDataSourceProperties;
import dev.linqibin.patra.catalog.adapter.scheduler.exception.RorConfigurationException;
import dev.linqibin.patra.catalog.adapter.scheduler.util.RorFileNameParser;
import dev.linqibin.patra.catalog.app.usecase.organization.command.RorOrganizationImportCommand;
import dev.linqibin.patra.catalog.app.usecase.organization.command.RorOrganizationImportResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// ROR 机构导入定时任务。
///
/// 通过 XXL-Job 控制台手动触发，执行 ROR（Research Organization Registry）机构数据的批量导入。
/// URL 从配置文件读取，版本号从文件名自动推断。
///
/// **JobHandler 名称**: `rorOrganizationImportJob`
///
/// **职责**：
///
/// - 协议转换：将 XXL-Job 请求转换为 Application 层 Command
/// - 日志记录：记录任务开始、结束和错误信息
/// - 结果报告：向 XXL-Job 控制台报告执行状态
///
/// **配置要求**：
///
/// ```yaml
/// patra:
///   catalog:
///     ror:
///       download-url:
// https://zenodo.org/records/17953395/files/v2.0-2025-12-16-ror-data.zip?download=1
/// ```
///
/// **ROR 数据来源**：
///
/// ROR 数据发布在 Zenodo，每个版本有独立的下载链接：
/// <https://ror.readme.io/docs/data-dump>
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
public class RorOrganizationImportScheduleJob {

  private final CommandBus commandBus;
  private final RorDataSourceProperties rorDataSourceProperties;

  /// 执行 ROR 机构导入任务。
  ///
  /// **JobHandler 名称**: `rorOrganizationImportJob`
  ///
  /// **无需参数**：URL 从配置文件读取，版本号从文件名自动推断。
  @XxlJob("rorOrganizationImportJob")
  public void executeRorOrganizationImport() {
    log.info("ROR 机构导入任务已触发，jobId [{}]", XxlJobHelper.getJobId());

    try {
      String url = rorDataSourceProperties.getDownloadUrl();
      String rorVersion = RorFileNameParser.extractVersion(url);
      log.info("ROR 机构配置：URL [{}]，版本 [{}]（从文件名推断）", url, rorVersion);

      // 调用 Application 层 Handler
      RorOrganizationImportResult result =
          commandBus.handle(new RorOrganizationImportCommand(url, rorVersion));

      handleSuccess(result);

    } catch (RorConfigurationException ex) {
      handleConfigurationError(ex);
    } catch (Exception ex) {
      handleExecutionError(ex);
    }
  }

  /// 处理配置错误。
  ///
  /// @param ex 配置异常
  private void handleConfigurationError(RorConfigurationException ex) {
    log.warn("ROR 机构导入任务配置错误：{}", ex.getMessage());
    XxlJobHelper.handleFail("配置错误：" + ex.getMessage());
  }

  /// 处理执行错误。
  ///
  /// @param ex 执行异常
  private void handleExecutionError(Exception ex) {
    log.error("ROR 机构导入任务执行失败：{}", ex.getMessage(), ex);
    XxlJobHelper.handleFail("执行失败：" + ex.getMessage());
  }

  /// 处理成功执行。
  ///
  /// @param result 导入结果
  private void handleSuccess(RorOrganizationImportResult result) {
    String message =
        String.format(
            "ROR 机构导入任务已启动，executionId [%d]，URL [%s]，版本 [%s]",
            result.executionId(), result.url(), result.rorVersion());
    log.info(message);
    XxlJobHelper.handleSuccess(message);
  }
}
