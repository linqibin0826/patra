package dev.linqibin.patra.catalog.adapter.scheduler.job;

import dev.linqibin.patra.catalog.adapter.scheduler.config.AuthorDataSourceProperties;
import dev.linqibin.patra.catalog.app.usecase.author.command.AuthorImportCommand;
import dev.linqibin.patra.catalog.app.usecase.author.dto.AuthorImportResult;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import dev.linqibin.commons.cqrs.CommandBus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// PubMed Computed Authors 导入定时任务。
///
/// 通过 XXL-Job 控制台手动触发，执行 PubMed Computed Authors 数据的批量导入。
/// URL 从配置文件读取。
///
/// **JobHandler 名称**: `authorImportJob`
///
/// **配置要求**：
///
/// ```yaml
/// patra:
///   catalog:
///     author:
///       computed-authors-url:
// https://ftp.ncbi.nlm.nih.gov/pub/lu/ComputedAuthors/computed_authors.json
/// ```
///
/// **数据源说明**：
///
/// - NLM FTP 站点的 PubMed Computed Authors JSON Lines 文件
/// - 文件约 3.6GB，包含约 2100 万+ 作者记录
/// - 预计执行时间 2-6 小时（取决于网络和数据库性能）
///
/// **导入策略**：
///
/// 纯 INSERT 策略，用于一次性数据初始化。如果表中已有数据，导入会失败。
///
/// **断点续传**：
///
/// 支持断点续传，失败后重新触发可从上次提交的 chunk 继续处理。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthorImportScheduleJob {

  private final CommandBus commandBus;
  private final AuthorDataSourceProperties authorDataSourceProperties;

  /// 执行 PubMed Computed Authors 导入任务。
  ///
  /// **JobHandler 名称**: `authorImportJob`
  ///
  /// **参数**：无需参数，URL 从配置文件读取。
  @XxlJob("authorImportJob")
  public void executeAuthorImport() {
    log.info("PubMed Computed Authors 导入任务已触发，jobId [{}]", XxlJobHelper.getJobId());

    try {
      String url = authorDataSourceProperties.getComputedAuthorsUrl();
      log.info("PubMed Computed Authors 配置：URL [{}]", url);

      AuthorImportResult result = commandBus.handle(AuthorImportCommand.of(url));
      handleSuccess(result.message());

    } catch (Exception ex) {
      handleExecutionError(ex);
      // 已通过 handleFail 报告失败，不再抛出异常
    }
  }

  /// 处理执行错误。
  private void handleExecutionError(Exception ex) {
    log.error("PubMed Computed Authors 导入任务执行失败：{}", ex.getMessage(), ex);
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
