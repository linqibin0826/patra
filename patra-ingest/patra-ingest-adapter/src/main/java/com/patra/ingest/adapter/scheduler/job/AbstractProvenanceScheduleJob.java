package com.patra.ingest.adapter.scheduler.job;

import cn.hutool.core.text.CharSequenceUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.enums.Priority;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.adapter.scheduler.param.ProvenanceScheduleJobParam;
import com.patra.ingest.app.usecase.plan.PlanIngestionUseCase;
import com.patra.ingest.app.usecase.plan.command.PlanIngestionCommand;
import com.patra.ingest.app.usecase.plan.dto.PlanIngestionResult;
import com.patra.ingest.domain.exception.IngestScheduleParameterException;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.Scheduler;
import com.patra.ingest.domain.model.enums.TriggerType;
import com.xxl.job.core.context.XxlJobHelper;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 调度任务基类,为 "来源 + 操作" 任务提供统一模板(参数解析 → 编排 → 结果/错误报告)。
 *
 * <p>通用逻辑集中在此处。子类只需定义 {@link #getProvenanceCode()} 和 {@link #getOperationCode()}。
 *
 * <p>默认值和约束: - 如果 XXL-Job 参数为空,则回退到默认窗口和步长(当前 step=P1D;以后可以外部化)。 - 将 windowFrom/windowTo 解析为
 * ISO-8601 Instant 字符串;缺失/非法值被视为 null。 - 非法的优先级值会被忽略以避免任务失败。 - 结果通过日志和 XxlJobHelper
 * 报告;失败时,抛出原始异常链以允许重试策略决定。
 */
@Slf4j
public abstract class AbstractProvenanceScheduleJob {

  private static final String DEFAULT_STEP = "P1D"; // 默认为 1 天,用于基于日期的切片(例如 PubMed)
  private static final String DEFAULT_SCHEDULER_LOG_ID = "0";

  /** 计划采集用例应用服务(应用层入口)。 */
  @Autowired private PlanIngestionUseCase planIngestionUseCase;

  /** JSON 对象映射器。 */
  @Autowired private ObjectMapper objectMapper;

  /**
   * 返回此任务的来源代码(每个子类固定)。
   *
   * @return 来源代码
   */
  protected abstract ProvenanceCode getProvenanceCode();

  /**
   * 返回此任务的操作代码(每个子类固定)。
   *
   * @return 操作代码
   */
  protected abstract OperationCode getOperationCode();

  /**
   * 将 XXL-Job JSON 参数解析为应用请求对象。
   *
   * <p>支持的字段: windowFrom, windowTo, priority, step, schedulerLogId, triggeredAt 以及任何额外字段(作为
   * triggerParams 传递)。
   *
   * <p>失败策略: 如果结构无效或 JSON 解析失败,抛出 {@link IngestScheduleParameterException},该异常会被任务入口点捕获并标记为失败。
   *
   * @param paramStr 原始 XXL-Job 参数(JSON 字符串;可能为空)
   * @return PlanIngestionCommand 请求
   * @throws IngestScheduleParameterException 当参数无效时
   */
  protected PlanIngestionCommand parseJobParam(String paramStr) {
    if (CharSequenceUtil.isBlank(paramStr)) {
      log.debug("使用默认任务配置,步长为 [{}]", DEFAULT_STEP);
      return buildPlanIngestionCommand(ProvenanceScheduleJobParam.empty(), Map.of());
    }
    try {
      Map<String, Object> rawParams = objectMapper.readValue(paramStr, new TypeReference<>() {});
      ProvenanceScheduleJobParam jobParam =
          rawParams == null
              ? ProvenanceScheduleJobParam.empty()
              : objectMapper.convertValue(rawParams, ProvenanceScheduleJobParam.class);
      if (jobParam == null) {
        jobParam = ProvenanceScheduleJobParam.empty();
      }

      Map<String, Object> triggerParams =
          (rawParams == null || rawParams.isEmpty())
              ? Map.of()
              : Collections.unmodifiableMap(new LinkedHashMap<>(rawParams));
      return buildPlanIngestionCommand(jobParam, triggerParams);
    } catch (Exception e) {
      throw new IngestScheduleParameterException("JSON 参数解析失败: " + e.getMessage(), e);
    }
  }

  private PlanIngestionCommand buildPlanIngestionCommand(
      ProvenanceScheduleJobParam param, Map<String, Object> triggerParams) {
    ProvenanceScheduleJobParam nonNullParam =
        param == null ? ProvenanceScheduleJobParam.empty() : param;
    Map<String, Object> nonNullTriggerParams = triggerParams == null ? Map.of() : triggerParams;
    PlanIngestionCommand command =
        new PlanIngestionCommand(
            getProvenanceCode(),
            getOperationCode(),
            resolveStep(nonNullParam.step()),
            TriggerType.SCHEDULE,
            Scheduler.XXL,
            String.valueOf(XxlJobHelper.getJobId()),
            resolveSchedulerLogId(nonNullParam.schedulerLogId()),
            parseInstant(nonNullParam.windowFrom(), "windowFrom"),
            parseInstant(nonNullParam.windowTo(), "windowTo"),
            resolvePriority(nonNullParam.priority()),
            resolveTriggeredAt(nonNullParam.triggeredAt()),
            nonNullTriggerParams);

    if (log.isDebugEnabled()) {
      log.debug(
          "已解析任务命令: 来源 [{}] 操作 [{}] 窗口 [{}, {}) 优先级 [{}]",
          command.provenanceCode(),
          command.operationCode(),
          command.windowFrom(),
          command.windowTo(),
          command.priority());
    }

    return command;
  }

  private String resolveStep(String step) {
    return CharSequenceUtil.isBlank(step) ? DEFAULT_STEP : CharSequenceUtil.trim(step);
  }

  private String resolveSchedulerLogId(String schedulerLogId) {
    return CharSequenceUtil.isBlank(schedulerLogId)
        ? DEFAULT_SCHEDULER_LOG_ID
        : CharSequenceUtil.trim(schedulerLogId);
  }

  private Priority resolvePriority(String priority) {
    if (CharSequenceUtil.isBlank(priority)) {
      return null;
    }
    String normalized = CharSequenceUtil.trim(priority).toUpperCase();
    try {
      return Priority.valueOf(normalized);
    } catch (IllegalArgumentException ex) {
      log.warn("忽略非法的优先级值: {}", priority);
      return null;
    }
  }

  private Instant parseInstant(String value, String fieldName) {
    if (CharSequenceUtil.isBlank(value)) {
      return null;
    }
    try {
      return Instant.parse(CharSequenceUtil.trim(value));
    } catch (Exception ex) {
      throw new IngestScheduleParameterException(
          String.format("字段 %s 的时间格式非法: %s", fieldName, value), ex);
    }
  }

  private Instant resolveTriggeredAt(String triggeredAt) {
    Instant parsed = parseInstant(triggeredAt, "triggeredAt");
    return parsed == null ? Instant.now() : parsed;
  }

  /**
   * 执行主调度流程: 日志记录(开始/结束/错误) + 参数解析 + 编排调用 + 结果报告。
   *
   * <p>执行时间会被记录用于 SLA 监控;失败时,XxlJobHelper 标记任务失败并重新抛出异常。
   *
   * @param paramStr XXL-Job JSON 参数字符串(可能为空)
   */
  protected void executeScheduleJob(String paramStr) {
    long startTime = System.currentTimeMillis();
    logJobStart(paramStr);

    try {
      PlanIngestionCommand command = parseJobParam(paramStr);
      PlanIngestionResult result = planIngestionUseCase.ingestPlan(command);
      handleJobSuccess(result, startTime);
    } catch (Exception e) {
      handleJobFailure(e, startTime);
      throw e;
    }
  }

  /** 记录任务开始,包含来源和操作上下文。 */
  private void logJobStart(String rawParam) {
    log.info(
        "开始为来源 [{}] 创建采集计划,操作为 [{}],使用参数: {}",
        getProvenanceCode().getCode(),
        getOperationCode(),
        rawParam);
  }

  /** 处理成功的任务执行,包含结果报告和日志记录。 */
  private void handleJobSuccess(PlanIngestionResult result, long startTime) {
    long duration = System.currentTimeMillis() - startTime;
    log.info(
        "已完成来源 [{}] 的采集计划 [{}],调度了 {} 个任务,耗时 {}ms",
        getProvenanceCode().getCode(),
        result.planId(),
        result.taskCount(),
        duration);

    XxlJobHelper.handleSuccess(
        String.format(
            "任务成功,耗时 %dms, planId=%s, taskCount=%d",
            duration, result.planId(), result.taskCount()));
  }

  /** 处理任务失败,包含错误日志和报告。 */
  private void handleJobFailure(Exception e, long startTime) {
    long duration = System.currentTimeMillis() - startTime;
    log.error(
        "来源 [{}] 的定时任务执行失败,操作为 [{}],耗时 {}ms: {}",
        getProvenanceCode().getCode(),
        getOperationCode(),
        duration,
        e.getMessage(),
        e);

    XxlJobHelper.handleFail(String.format("任务失败: %s", e.getMessage()));
  }
}
