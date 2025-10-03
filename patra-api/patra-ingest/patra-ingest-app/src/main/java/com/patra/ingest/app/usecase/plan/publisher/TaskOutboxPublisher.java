package com.patra.ingest.app.usecase.plan.publisher;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.ingest.app.usecase.relay.support.OutboxChannels;
import com.patra.ingest.domain.event.TaskQueuedEvent;
import com.patra.ingest.domain.exception.PlanPersistenceException;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.enums.OutboxStatus;
import com.patra.ingest.domain.port.OutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 任务 Outbox 发布器（应用层基础设施适配）。
 * <p>
 * 职责：
 * <ul>
 *   <li>接收任务入队事件（首发 / 补偿）</li>
 *   <li>构造幂等可重放的 OutboxMessage（含 headers / payload / dedupKey / partitionKey）</li>
 *   <li>持久化到 Outbox 存储，供后续 Relay 可靠转发到 MQ</li>
 * </ul>
 * </p>
 * <h4>幂等策略</h4>
 * <ul>
 *   <li>首次发布：按事件（taskId + idempotentKey）生成消息，若 taskId 为空直接跳过（说明未持久化成功）</li>
 *   <li>补偿发布：根据 (channel, dedupKey) 查询；存在则刷新状态 / 负载 / 头部并重置重试计数，不存在则创建</li>
 * </ul>
 * <h4>分区策略</h4>
 * <p>partitionKey = provenance:operation（缺失字段退化拼接），用于下游确保同来源 + 操作顺序。</p>
 * <h4>延时发布</h4>
 * <p>notBefore = scheduledAt（为空则即时）。</p>
 * <h4>失败模式</h4>
 * <ul>
 *   <li>JSON 序列化 / 解析失败 → 抛出 {@link com.patra.ingest.domain.exception.PlanPersistenceException}</li>
 *   <li>仓储 save / saveOrUpdate 异常 → 向上抛出，由上层事务框架回滚</li>
 * </ul>
 * <h4>日志策略</h4>
 * <ul>
 *   <li>WARN：跳过无 taskId 事件</li>
 *   <li>INFO：补偿刷新或新建 retry outbox</li>
 *   <li>DEBUG：可在未来扩展打印更细粒度调试（当前避免噪声）</li>
 * </ul>
 * <h4>线程安全</h4>
 * <p>无共享可变状态（仓储 / ObjectMapper 为线程安全用法），组件为无状态单例。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskOutboxPublisher {

    private static final String AGGREGATE_TYPE_TASK = "TASK";
    /** 默认通道，可进一步下沉到配置中心。 */
    private static final String DEFAULT_CHANNEL = OutboxChannels.INGEST_TASK_READY;
    /** 默认业务操作类型标识。 */
    private static final String DEFAULT_OP_TYPE = "TASK_READY";

    /** Outbox 仓储 */
    private final OutboxMessageRepository outboxMessageRepository;
    /** JSON 处理器 */
    private final ObjectMapper objectMapper;

    /**
     * 首次发布任务入队事件。
     * <p>遍历事件构造消息：若 taskId 为空（任务尚未持久化成功）将跳过，避免孤儿消息。</p>
     * <h4>复杂度</h4>
     * <p>O(n) n=事件数。</p>
     *
     * @param events 入队事件列表（为空/空集合直接返回，不抛错）
     * @param plan 关联计划（必须非 null）
     * @param schedule 调度实例（必须非 null）
     */
    public void publish(List<TaskQueuedEvent> events,
                        PlanAggregate plan,
                        ScheduleInstanceAggregate schedule) {
        if (CollUtil.isEmpty(events)) {
            return;
        }
        Objects.requireNonNull(plan, "plan must not be null");
        Objects.requireNonNull(schedule, "schedule must not be null");

        List<OutboxMessage> messages = new ArrayList<>(events.size());
        for (TaskQueuedEvent event : events) {
            if (event.taskId() == null) {
                log.warn("[INGEST][APP] skip task event without persistence, planId={}", event.planId());
                continue;
            }
            // 生成 payload / headers，保证消息具备幂等信息
            ObjectNode payloadNode = buildPayload(event, plan);
            ObjectNode headersNode = buildHeaders(event, schedule, plan);
            OutboxMessage message = OutboxMessage.builder()
                    .aggregateType(AGGREGATE_TYPE_TASK)
                    .aggregateId(event.taskId())
                    .channel(DEFAULT_CHANNEL)
                    .opType(DEFAULT_OP_TYPE)
                    .partitionKey(buildPartitionKey(event))
                    .dedupKey(event.idempotentKey())
                    .payloadJson(writeJson(payloadNode))
                    .headersJson(writeJson(headersNode))
                    .notBefore(resolveNotBefore(event.scheduledAt()))
                    .retryCount(0)
                    .build();
            messages.add(message);
        }

        if (!messages.isEmpty()) {
            // 一次性批量持久化，降低数据库往返
            outboxMessageRepository.saveAll(messages);
        }
    }

    /**
     * 补偿场景发布（刷新或新增）。
     * <p>逻辑：按 (channel, dedupKey) 查找 → 刷新状态与负载 → 未命中则新建。</p>
     * <h4>状态重置</h4>
     * <ul>
     *   <li>statusCode → PENDING</li>
     *   <li>retryCount → 0；nextRetryAt / error 字段清空</li>
     *   <li>lease / msgId 清空：释放占用以便 Relay 重新获取</li>
     * </ul>
     * <h4>复杂度</h4>
     * <p>O(n) + 仓储查询次数 n。</p>
     *
     * @param events 入队事件列表（为空时直接返回）
     * @param plan 关联计划（非 null）
     * @param schedule 调度实例（非 null）
     */
    public void publishRetry(List<TaskQueuedEvent> events,
                             PlanAggregate plan,
                             ScheduleInstanceAggregate schedule) {
        if (CollUtil.isEmpty(events)) {
            return;
        }
        Objects.requireNonNull(plan, "plan must not be null");
        Objects.requireNonNull(schedule, "schedule must not be null");

        for (TaskQueuedEvent event : events) {
            ObjectNode payloadNode = buildPayload(event, plan);
            ObjectNode headersNode = buildHeaders(event, schedule, plan);
            String payloadJson = writeJson(payloadNode);
            String headersJson = writeJson(headersNode);
            Instant notBefore = resolveNotBefore(event.scheduledAt());

            Optional<OutboxMessage> existing = outboxMessageRepository.findByChannelAndDedup(DEFAULT_CHANNEL, event.idempotentKey());
            if (existing.isPresent()) {
                OutboxMessage refreshed = existing.get().toBuilder()
                        .statusCode(OutboxStatus.PENDING.name())
                        .retryCount(0)
                        .nextRetryAt(null)
                        .errorCode(null)
                        .errorMsg(null)
                        .payloadJson(payloadJson)
                        .headersJson(headersJson)
                        .notBefore(notBefore)
                        .leaseOwner(null)
                        .leaseExpireAt(null)
                        .msgId(null)
                        .build();
                outboxMessageRepository.saveOrUpdate(refreshed);
                log.info("[INGEST][APP] Refreshed outbox message for retry, aggregateId={}, dedupKey={}", event.taskId(), event.idempotentKey());
            } else {
                OutboxMessage message = OutboxMessage.builder()
                        .aggregateType(AGGREGATE_TYPE_TASK)
                        .aggregateId(event.taskId())
                        .channel(DEFAULT_CHANNEL)
                        .opType(DEFAULT_OP_TYPE)
                        .partitionKey(buildPartitionKey(event))
                        .dedupKey(event.idempotentKey())
                        .payloadJson(payloadJson)
                        .headersJson(headersJson)
                        .notBefore(notBefore)
                        .retryCount(0)
                        .build();
                outboxMessageRepository.saveOrUpdate(message);
                log.info("[INGEST][APP] Created new retry outbox message, aggregateId={}, dedupKey={}", event.taskId(), event.idempotentKey());
            }
        }
    }

    /**
     * 构建消息负载。
     *
     * @param event 任务入队事件
     * @param plan 关联计划
     * @return JSON 负载
     */
    private ObjectNode buildPayload(TaskQueuedEvent event, PlanAggregate plan) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("taskId", event.taskId());
        payload.put("planId", event.planId());
        if (event.sliceId() == null) {
            payload.putNull("sliceId");
        } else {
            payload.put("sliceId", event.sliceId());
        }
        payload.put("provenance", event.provenanceCode());
        payload.put("operation", event.operationCode());
        payload.put("idempotentKey", event.idempotentKey());
        if (event.priority() != null) {
            payload.put("priority", event.priority());
        } else {
            payload.putNull("priority");
        }
        if (event.scheduledAt() != null) {
            payload.put("scheduledAt", event.scheduledAt().toString());
        }
        if (CharSequenceUtil.isNotBlank(event.paramsJson())) {
            payload.set("params", readJsonNode(event.paramsJson()));
        }
        payload.put("planKey", plan.getPlanKey());
        if (plan.getWindowFrom() != null) {
            payload.put("planWindowFrom", plan.getWindowFrom().toString());
        }
        if (plan.getWindowTo() != null) {
            payload.put("planWindowTo", plan.getWindowTo().toString());
        }
        payload.put("planSliceStrategy", plan.getSliceStrategyCode());
        if (CharSequenceUtil.isNotBlank(plan.getSliceParamsJson())) {
            payload.set("planSliceParams", readJsonNode(plan.getSliceParamsJson()));
        }
        return payload;
    }

    /**
     * 构建消息头部，仅传递必要的追踪信息。
     * <p>
     * <strong>设计原则</strong>：Headers 应该是轻量的，仅用于路由、追踪和调试，
     * 不应包含业务数据。大部分上下文信息应放在 payload 中。
     * </p>
     * <p>
     * <strong>当前策略</strong>：仅保留调度追踪信息，便于关联日志和问题排查。
     * </p>
     *
     * @param event 任务事件
     * @param schedule 调度实例
     * @param plan 计划信息（未使用，保留以便将来扩展）
     * @return JSON 头部（包含追踪信息）
     */
    private ObjectNode buildHeaders(TaskQueuedEvent event,
                                    ScheduleInstanceAggregate schedule,
                                    PlanAggregate plan) {
        ObjectNode headers = JsonNodeFactory.instance.objectNode();
        
        // 调度追踪：用于关联调度日志和问题排查
        headers.put("scheduleInstanceId", schedule.getId());
        headers.put("scheduler", schedule.getScheduler().name());
        if (schedule.getSchedulerJobId() != null) {
            headers.put("schedulerJobId", schedule.getSchedulerJobId());
        }
        
        // 时间追踪：用于延迟分析和性能监控
        headers.put("triggeredAt", schedule.getTriggeredAt().toString());
        headers.put("occurredAt", event.occurredAt().toString());
        
        // 注意：其他业务信息（planKey、planOperation、planEndpoint 等）已包含在 payload 中，
        // 无需在 headers 中重复传递，避免数据冗余和网络开销
        
        return headers;
    }

    /**
     * 序列化 JSON 节点。
     *
     * @param node JSON 节点
     * @return 序列化字符串
     */
    private String writeJson(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new PlanPersistenceException(PlanPersistenceException.Stage.TASK,
                    "序列化 Outbox 消息负载失败", e);
        }
    }

    /**
     * 读取 JSON 字符串为节点。
     *
     * @param json JSON 字符串
     * @return 解析后的节点
     */
    private JsonNode readJsonNode(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new PlanPersistenceException(PlanPersistenceException.Stage.TASK,
                    "解析任务参数 JSON 失败", e);
        }
    }

    /**
     * 解析最早发布时间。
     *
     * @param scheduledAt 任务调度时间
     * @return NotBefore 时间
     */
    private Instant resolveNotBefore(Instant scheduledAt) {
        return scheduledAt == null ? Instant.now() : scheduledAt;
    }

    /**
     * 生成分区键，确保同来源同操作在下游保持顺序。
     *
     * @param event 任务事件
     * @return 分区键
     */
    private String buildPartitionKey(TaskQueuedEvent event) {
        String provenance = StrUtil.nullToEmpty(event.provenanceCode());
        String operation = StrUtil.nullToEmpty(event.operationCode());
        if (StrUtil.isEmpty(provenance) && StrUtil.isEmpty(operation)) {
            return "TASK";
        }
        if (StrUtil.isEmpty(provenance)) {
            return operation;
        }
        if (StrUtil.isEmpty(operation)) {
            return provenance;
        }
        return provenance + ':' + operation;
    }
}
