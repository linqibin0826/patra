package dev.patra.catalog.adapter.scheduler.job;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import dev.patra.catalog.adapter.scheduler.config.PubmedDataSourceProperties;
import dev.patra.catalog.app.usecase.publication.update.command.PubmedUpdateSyncCommand;
import dev.patra.catalog.app.usecase.publication.update.dto.PubmedUpdateSyncResult;
import dev.patra.catalog.domain.model.enums.PubmedUpdateScope;
import dev.patra.tool.cqrs.CommandBus;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// PubMed updatefiles 增量同步的 XXL-Job 入口（handler `pubmedUpdateSyncJob`）。
///
/// 参数（均可省）：`fromFile=N,maxFiles=M,scope=EXISTING_ONLY|ALL`。省略 `scope` 时取配置
/// `patra.catalog.pubmed.update-scope`；目录地址、文件名前缀与起点也从配置补齐。
/// 另一个实例在运行时本次也算成功，并在消息里写明。建任务的步骤见
/// `docs/patra/runbooks/v0.11-fulltext-runbook.md`。
///
/// @author linqibin
/// @since 0.11.0
@Slf4j
@Component
@RequiredArgsConstructor
public class PubmedUpdateSyncScheduleJob {

  private static final String PARAM_FORMAT = "fromFile=N,maxFiles=M,scope=EXISTING_ONLY|ALL";

  private final CommandBus commandBus;
  private final PubmedDataSourceProperties pubmedDataSourceProperties;

  /// 执行一次增量同步。
  @XxlJob("pubmedUpdateSyncJob")
  public void execute() {
    log.info("PubMed 增量同步任务已触发，jobId [{}]", XxlJobHelper.getJobId());
    PubmedUpdateSyncCommand command;
    try {
      command = buildCommand(XxlJobHelper.getJobParam());
    } catch (IllegalArgumentException ex) {
      log.warn("PubMed 增量同步任务参数错误：{}", ex.getMessage());
      XxlJobHelper.handleFail("参数错误：" + ex.getMessage());
      return;
    }

    try {
      PubmedUpdateSyncResult result = commandBus.handle(command);
      log.info(result.message());
      XxlJobHelper.handleSuccess(result.message());
    } catch (Exception ex) {
      log.error("PubMed 增量同步任务执行失败：{}", ex.getMessage(), ex);
      XxlJobHelper.handleFail("执行失败：" + ex.getMessage());
    }
  }

  /// 解析任务参数并用配置补齐命令。
  private PubmedUpdateSyncCommand buildCommand(String jobParam) {
    Integer fromFile = null;
    Integer maxFiles = null;
    PubmedUpdateScope scope = pubmedDataSourceProperties.getUpdateScope();
    if (jobParam != null && !jobParam.isBlank()) {
      Set<String> seenKeys = new HashSet<>();
      for (String pair : jobParam.split(",")) {
        String[] keyValue = pair.split("=", 2);
        if (keyValue.length != 2 || keyValue[0].isBlank()) {
          throw new IllegalArgumentException("参数格式无效，期望：" + PARAM_FORMAT + "，实际：" + jobParam);
        }
        String key = keyValue[0].trim();
        String value = keyValue[1].trim();
        if (!seenKeys.add(key)) {
          throw new IllegalArgumentException("参数 key [%s] 重复，实际：%s".formatted(key, jobParam));
        }
        switch (key) {
          case "fromFile" -> fromFile = parsePositiveInt(key, value);
          case "maxFiles" -> maxFiles = parsePositiveInt(key, value);
          case "scope" -> scope = parseScope(value);
          default ->
              throw new IllegalArgumentException(
                  "未知参数 key [%s]，期望：%s，实际：%s".formatted(key, PARAM_FORMAT, jobParam));
        }
      }
    }
    return PubmedUpdateSyncCommand.builder()
        .updateUrl(pubmedDataSourceProperties.getUpdateUrl())
        .filePrefix(pubmedDataSourceProperties.getFilePrefix())
        .startSeq(pubmedDataSourceProperties.getUpdateStartSeq())
        .scope(scope)
        .fromFile(fromFile)
        .maxFiles(maxFiles)
        .build();
  }

  /// 解析正整数参数。
  private static int parsePositiveInt(String key, String value) {
    int parsed;
    try {
      parsed = Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(key + " 必须是整数，实际：'" + value + "'", e);
    }
    if (parsed < 1) {
      throw new IllegalArgumentException(key + " 必须 >= 1，实际：'" + value + "'");
    }
    return parsed;
  }

  /// 解析导入范围，大小写不敏感。
  private static PubmedUpdateScope parseScope(String value) {
    try {
      return PubmedUpdateScope.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("scope 只能是 EXISTING_ONLY 或 ALL，实际：'" + value + "'", e);
    }
  }
}
