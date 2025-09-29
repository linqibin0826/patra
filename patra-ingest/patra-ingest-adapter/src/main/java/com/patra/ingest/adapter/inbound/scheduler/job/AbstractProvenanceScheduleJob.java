package com.patra.ingest.adapter.inbound.scheduler.job;

import cn.hutool.core.text.CharSequenceUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.enums.ProvenanceCode;
import com.patra.common.enums.Priority;
import com.patra.ingest.adapter.inbound.scheduler.param.ProvenanceScheduleJobParam;
import com.patra.ingest.app.planning.application.PlanIngestionUseCase;
import com.patra.ingest.app.planning.command.PlanIngestionRequest;
import com.patra.ingest.app.planning.dto.PlanIngestionResult;
import com.patra.ingest.domain.exception.IngestScheduleParameterException;
import com.patra.ingest.domain.model.enums.Endpoint;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.Scheduler;
import com.patra.ingest.domain.model.enums.TriggerType;
import com.xxl.job.core.context.XxlJobHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 调度任务抽象基类：为“来源 + 操作”类型任务提供统一模板（参数解析 → 编排调用 → 结果/异常上报）。
 * <p>抽象出公共逻辑，子类仅需固化 {@link #getProvenanceCode()}、{@link #getOperationCode()} 以及可选覆盖 {@link #getEndpoint()}。</p>
 * <p>默认行为与约束：
 * <ul>
 *   <li>XXL 参数为空时使用默认窗口 & 默认周期（当前固定 step=PT6H，可后续外部化配置）。</li>
 *   <li>解析 windowFrom/windowTo 为 ISO-8601 Instant 字符串，非法或缺失忽略。</li>
 *   <li>priority 非法值静默忽略，避免调度失败。</li>
 *   <li>返回结果通过日志与 XxlJobHelper 上报，失败时抛出原始异常链，方便上层重试策略决策。</li>
 * </ul>
 * </p>
 */
@Slf4j
public abstract class AbstractProvenanceScheduleJob {

    private static final String DEFAULT_STEP = "PT6H";
    private static final String DEFAULT_SCHEDULER_LOG_ID = "0";

    /** 计划编排应用服务（应用层入口）。 */
    @Autowired
    private PlanIngestionUseCase planIngestionUseCase;

    /** JSON 解析器。 */
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 获取当前任务对应的来源代码（子类固定）。
     * @return 数据来源枚举
     */
    protected abstract ProvenanceCode getProvenanceCode();

    /**
     * 获取当前任务对应的操作类型（子类固定）。
     * @return 操作类型枚举
     */
    protected abstract OperationCode getOperationCode();

    /**
     * 获取端点名称，默认 SEARCH；子类可覆盖扩展如 STATUS、UPDATE 等。
     * @return 端点枚举
     */
    protected Endpoint getEndpoint() {
        return Endpoint.SEARCH;
    }

    /**
     * 解析 XXL-Job 参数 JSON，构建应用层请求对象。
     * <p>支持字段：windowFrom、windowTo、priority、step、schedulerLogId、triggeredAt 以及任意扩展字段（透传为 triggerParams）。</p>
     * <p>失败策略：结构非法或 JSON 读取失败抛出 {@link IngestScheduleParameterException}，由调度入口捕获并标记失败。</p>
     * @param paramStr 原始 XXL Job 参数（JSON 字符串，可为空）
     * @return PlanIngestionRequest 请求
     * @throws IngestScheduleParameterException 参数不合法时抛出
     */
    protected PlanIngestionRequest parseJobParam(String paramStr) {
        if (CharSequenceUtil.isBlank(paramStr)) {
            return buildPlanIngestionRequest(ProvenanceScheduleJobParam.empty(), Map.of());
        }
        try {
            Map<String, Object> rawParams = objectMapper.readValue(paramStr, new TypeReference<>() { });
            ProvenanceScheduleJobParam jobParam = rawParams == null
                    ? ProvenanceScheduleJobParam.empty()
                    : objectMapper.convertValue(rawParams, ProvenanceScheduleJobParam.class);
            if (jobParam == null) {
                jobParam = ProvenanceScheduleJobParam.empty();
            }
            Map<String, Object> triggerParams = (rawParams == null || rawParams.isEmpty())
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(rawParams));
            return buildPlanIngestionRequest(jobParam, triggerParams);
        } catch (Exception e) {
            throw new IngestScheduleParameterException("JSON参数解析失败: " + e.getMessage(), e);
        }
    }

    private PlanIngestionRequest buildPlanIngestionRequest(ProvenanceScheduleJobParam param, Map<String, Object> triggerParams) {
        ProvenanceScheduleJobParam nonNullParam = param == null ? ProvenanceScheduleJobParam.empty() : param;
        Map<String, Object> nonNullTriggerParams = triggerParams == null ? Map.of() : triggerParams;
        return new PlanIngestionRequest(
                getProvenanceCode(),
                getEndpoint(),
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
                nonNullTriggerParams
        );
    }

    private String resolveStep(String step) {
        return CharSequenceUtil.isBlank(step) ? DEFAULT_STEP : CharSequenceUtil.trim(step);
    }

    private String resolveSchedulerLogId(String schedulerLogId) {
        return CharSequenceUtil.isBlank(schedulerLogId) ? DEFAULT_SCHEDULER_LOG_ID : CharSequenceUtil.trim(schedulerLogId);
    }

    private Priority resolvePriority(String priority) {
        if (CharSequenceUtil.isBlank(priority)) {
            return null;
        }
        String normalized = CharSequenceUtil.trim(priority).toUpperCase();
        try {
            return Priority.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            log.warn("[INGEST][ADAPTER] 忽略非法优先级取值: {}", priority);
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
            throw new IngestScheduleParameterException(String.format("字段 %s 的时间格式非法: %s", fieldName, value), ex);
        }
    }

    private Instant resolveTriggeredAt(String triggeredAt) {
        Instant parsed = parseInstant(triggeredAt, "triggeredAt");
        return parsed == null ? Instant.now() : parsed;
    }

    /**
     * 执行调度任务主流程：日志埋点（开始/结束/异常）+ 参数解析 + 编排调用 + 结果上报。
     * <p>计时用于后续 SLA 监控；失败时通过 XxlJobHelper 标记并抛出异常。</p>
     * @param paramStr XXL-Job JSON 参数字符串（可为空）
     */
    protected void executeScheduleJob(String paramStr) {
    // 记录执行起始时间，便于统计 SLA 耗时
    long startTime = System.currentTimeMillis();

        try {
        log.info("[INGEST][ADAPTER] Starting scheduled job, provenance={}, endpoint={}, operation={}, rawParam={}",
                    getProvenanceCode().getCode(), getEndpoint(), getOperationCode(), paramStr);

            PlanIngestionRequest command = parseJobParam(paramStr);
            PlanIngestionResult result = planIngestionUseCase.ingestPlan(command);

            long duration = System.currentTimeMillis() - startTime;
        log.info("[INGEST][ADAPTER] Scheduled job completed, provenance={}, endpoint={}, operation={}, durationMs={}, planId={}, taskCount={}",
                    getProvenanceCode().getCode(), getEndpoint(), getOperationCode(), duration, result.planId(), result.taskCount());

            XxlJobHelper.handleSuccess(String.format("Job succeeded in %dms, planId=%s, taskCount=%d",
                    duration, result.planId(), result.taskCount()));

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
        log.error("[INGEST][ADAPTER] Scheduled job failed, provenance={}, endpoint={}, operation={}, durationMs={}, error={}",
                    getProvenanceCode().getCode(), getEndpoint(), getOperationCode(), duration, e.getMessage(), e);

            XxlJobHelper.handleFail(String.format("Job failed: %s", e.getMessage()));
            throw e;
        }
    }
}
