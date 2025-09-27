package com.patra.ingest.adapter.inbound.scheduler.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.patra.common.enums.ProvenanceCode;
import com.patra.common.enums.Priority;
import com.patra.ingest.domain.model.enums.TriggerType;
import com.patra.ingest.domain.model.enums.Scheduler;
import com.patra.ingest.app.orchestration.command.PlanIngestionRequest;
import com.patra.ingest.app.orchestration.dto.PlanIngestionResult;
import com.patra.ingest.app.orchestration.application.PlanIngestionUseCase;
import com.patra.ingest.domain.model.enums.Endpoint;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.xxl.job.core.context.XxlJobHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Map;

/**
 * 抽象的 Provenance 调度任务基类。
 * <p>为各个数据来源提供统一的 XXL-Job 处理模板，负责参数解析、执行编排与异常处理。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public abstract class AbstractProvenanceScheduleJob {

    /**
     * 计划编排应用服务
     */
    @Autowired
    private PlanIngestionUseCase planIngestionUseCase;

    /**
     * JSON 解析器
     */
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 获取当前任务对应的来源代码。
     *
     * @return 数据来源枚举
     */
    protected abstract ProvenanceCode getProvenanceCode();

    /**
     * 获取当前任务对应的操作类型（由子类固定）。
     *
     * @return 操作类型枚举
     */
    protected abstract OperationCode getOperationCode();

    /**
     * 获取端点名称，默认 SEARCH，子类可覆盖。
     *
     * @return 端点枚举
     */
    protected Endpoint getEndpoint() {
        return Endpoint.SEARCH;
    }

    /**
     * 解析 XXL-Job 参数。
     *
     * @param paramStr JSON 格式的参数字符串
     * @return 解析后的应用层请求
     */
    protected PlanIngestionRequest parseJobParam(String paramStr) {
        // 空参数 -> 使用默认触发配置
        if (paramStr == null || paramStr.trim().isEmpty()) {
            return new PlanIngestionRequest(
                    getProvenanceCode(),
                    getEndpoint(),
                    getOperationCode(),
                    "PT6H",
                    TriggerType.SCHEDULE,
                    Scheduler.XXL,
                    XxlJobHelper.getJobId() + "",
                    "0",
                    null,
                    null,
                    null,
                    Instant.now(),
                    Map.of());
        }
        try {
            JsonNode root = objectMapper.readTree(paramStr);
            if (!root.isObject()) {
                throw new IllegalArgumentException("任务参数必须为JSON对象");
            }
            Map<String, Object> triggerParams = objectMapper.convertValue(root, new TypeReference<>() {
            });

            Instant windowFrom = null;
            if (root.hasNonNull("windowFrom")) {
                String wf = root.get("windowFrom").asText();
                if (!wf.isEmpty()) {
                    windowFrom = Instant.parse(wf);
                }
            }
            Instant windowTo = null;
            if (root.hasNonNull("windowTo")) {
                String wt = root.get("windowTo").asText();
                if (!wt.isEmpty()) {
                    windowTo = Instant.parse(wt);
                }
            }
            Priority priority = null;
            if (root.hasNonNull("priority")) {
                try {
                    priority = Priority.valueOf(root.get("priority").asText().toUpperCase());
                } catch (Exception ignored) {
                    // 忽略无法解析的优先级取值
                }
            }
            return new PlanIngestionRequest(
                    getProvenanceCode(),
                    getEndpoint(),
                    getOperationCode(),
                    "PT6H",
                    TriggerType.SCHEDULE,
                    Scheduler.XXL,
                    XxlJobHelper.getJobId() + "",
                    "0",
                    windowFrom,
                    windowTo,
                    priority,
                    Instant.now(),
                    triggerParams);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON参数解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行调度任务的通用逻辑。
     *
     * @param paramStr XXL-Job JSON 参数字符串
     */
    protected void executeScheduleJob(String paramStr) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("Starting scheduled job, provenance={}, endpoint={}, operation={}, rawParam={}",
                    getProvenanceCode().getCode(), getEndpoint(), getOperationCode(), paramStr);

            PlanIngestionRequest command = parseJobParam(paramStr);
            PlanIngestionResult result = planIngestionUseCase.ingestPlan(command);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Scheduled job completed, provenance={}, endpoint={}, operation={}, durationMs={}, planId={}, taskCount={}",
                    getProvenanceCode().getCode(), getEndpoint(), getOperationCode(), duration, result.planId(), result.taskCount());

            XxlJobHelper.handleSuccess(String.format("Job succeeded in %dms, planId=%s, taskCount=%d",
                    duration, result.planId(), result.taskCount()));

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Scheduled job failed, provenance={}, endpoint={}, operation={}, durationMs={}, error={}",
                    getProvenanceCode().getCode(), getEndpoint(), getOperationCode(), duration, e.getMessage(), e);

            XxlJobHelper.handleFail(String.format("Job failed: %s", e.getMessage()));
            throw e;
        }
    }
}
