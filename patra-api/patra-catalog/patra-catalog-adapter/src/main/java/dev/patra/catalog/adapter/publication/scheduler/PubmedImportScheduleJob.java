package dev.patra.catalog.adapter.publication.scheduler;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import dev.patra.catalog.adapter.publication.config.PubmedDataSourceProperties;
import dev.patra.catalog.app.publication.usecase.sync.PubmedFileImportCommand;
import dev.patra.catalog.app.publication.usecase.sync.PubmedFileImportResult;
import dev.patra.catalog.domain.kernel.exception.CatalogScheduleParameterException;
import dev.patra.catalog.domain.publication.model.enums.PubmedFileKind;
import dev.patra.catalog.domain.publication.model.enums.PubmedImportScope;
import dev.patra.tool.cqrs.CommandBus;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// PubMed 文件导入的 XXL-Job 入口（handler `pubmedImportJob`），每日增量也用它。
///
/// 参数（均可省）：
/// `kind=BASELINE|UPDATE,from=N,to=M,maxFiles=K,scope=EXISTING_ONLY|ALL,reprocess=true|false`。
/// 无参数 = 每日增量（UPDATE、从进度续跑、到最新）。`kind=BASELINE` 必须给 `from`。省略 `scope` 时取配置
/// `patra.catalog.pubmed.scope`（默认 `EXISTING_ONLY`）；目录地址、文件名前缀与起点从配置补齐。
/// 另一个实例（导入或本地重算）在运行时本次也算成功，并在消息里写明。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@RequiredArgsConstructor
public class PubmedImportScheduleJob {

  private static final String PARAM_FORMAT =
      "kind=BASELINE|UPDATE,from=N,to=M,maxFiles=K,scope=EXISTING_ONLY|ALL,reprocess=true|false";

  private final CommandBus commandBus;
  private final PubmedDataSourceProperties pubmedDataSourceProperties;

  /// 执行一次。
  @XxlJob("pubmedImportJob")
  public void execute() {
    log.info("PubMed 导入任务已触发，jobId [{}]", XxlJobHelper.getJobId());
    PubmedFileImportCommand command;
    try {
      command = buildCommand(XxlJobHelper.getJobParam());
    } catch (IllegalArgumentException | CatalogScheduleParameterException ex) {
      log.warn("PubMed 导入任务参数错误：{}", ex.getMessage());
      XxlJobHelper.handleFail("参数错误：" + ex.getMessage());
      return;
    }

    try {
      PubmedFileImportResult result = commandBus.handle(command);
      log.info(result.message());
      XxlJobHelper.handleSuccess(result.message());
    } catch (Exception ex) {
      log.error("PubMed 导入任务执行失败：{}", ex.getMessage(), ex);
      XxlJobHelper.handleFail("执行失败：" + ex.getMessage());
    }
  }

  /// 解析任务参数并用配置补齐命令。
  ///
  /// @param jobParam 任务参数，可空
  /// @return 命令
  /// @throws IllegalArgumentException 参数格式不对时
  private PubmedFileImportCommand buildCommand(String jobParam) {
    PubmedFileKind kind = PubmedFileKind.UPDATE;
    Integer from = null;
    Integer to = null;
    Integer maxFiles = null;
    PubmedImportScope scope = pubmedDataSourceProperties.getScope();
    boolean reprocess = false;
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
          case "kind" -> kind = parseEnum(PubmedFileKind.class, key, value);
          case "from" -> from = parsePositiveInt(key, value);
          case "to" -> to = parsePositiveInt(key, value);
          case "maxFiles" -> maxFiles = parsePositiveInt(key, value);
          case "scope" -> scope = parseEnum(PubmedImportScope.class, key, value);
          case "reprocess" -> reprocess = parseBoolean(key, value);
          default ->
              throw new IllegalArgumentException(
                  "未知参数 key [%s]，期望：%s，实际：%s".formatted(key, PARAM_FORMAT, jobParam));
        }
      }
    }
    return PubmedFileImportCommand.builder()
        .kind(kind)
        .baseUrl(
            kind == PubmedFileKind.BASELINE
                ? pubmedDataSourceProperties.getBaselineUrl()
                : pubmedDataSourceProperties.getUpdateUrl())
        .filePrefix(pubmedDataSourceProperties.getFilePrefix())
        .startSeq(pubmedDataSourceProperties.getUpdateStartSeq())
        .scope(scope)
        .from(from)
        .to(to)
        .maxFiles(maxFiles)
        .reprocess(reprocess)
        .build();
  }

  /// 解析正整数参数。
  ///
  /// @param key 参数名
  /// @param value 原值
  /// @return 正整数
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

  /// 解析 `true` / `false`（大小写不敏感），其他值报错。
  ///
  /// @param key 参数名
  /// @param value 原值
  /// @return 布尔值
  private static boolean parseBoolean(String key, String value) {
    String lower = value.toLowerCase(Locale.ROOT);
    if (!"true".equals(lower) && !"false".equals(lower)) {
      throw new IllegalArgumentException(key + " 只能是 true 或 false，实际：'" + value + "'");
    }
    return Boolean.parseBoolean(lower);
  }

  /// 解析枚举参数（大小写不敏感）。
  ///
  /// @param type 枚举类型
  /// @param key 参数名
  /// @param value 原值
  /// @param <E> 枚举
  /// @return 枚举值
  private static <E extends Enum<E>> E parseEnum(Class<E> type, String key, String value) {
    try {
      return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(key + " 取值不对，实际：'" + value + "'", e);
    }
  }
}
