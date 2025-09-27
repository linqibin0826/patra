package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/**
 * <p><b>Outbox 消息 DO</b> —— 映射表：<code>ing_outbox_message</code></p>
 * <p>
 * 语义：与业务数据<strong>同一事务</strong>落库的通用出站消息（任务推送 / 集成事件统一托管）。
 * Relay 仅扫描本表并发布到 MQ（如 RocketMQ），不扫描业务热表，确保写侧最小侵入与发布解耦。
 * </p>
 * <p>
 * 关键规则：
 * <ul>
 *   <li>幂等：(<code>channel</code>, <code>dedup_key</code>) 唯一（UK：uk_outbox_channel_dedup），保障源端去重；可安全重试。</li>
 *   <li>顺序/分区：<code>partition_key</code> 建议取值 "<code>provenance:operation</code>"，
 *       通过索引 <code>idx_outbox_partition(channel, partition_key, status_code)</code> 控制并发与保序（例如：<code>PUBMED:HARVEST</code>）。</li>
 *   <li>定时/延时：<code>not_before</code> 为最早可发布时间（UTC），NULL 表示随时可发；
 *       Relay 扫描通常按 <code>status_code</code> + 时间游标（<code>idx_outbox_status_time</code>）批量拉取。</li>
 *   <li>租约：<code>pub_lease_owner</code>/<code>pub_leased_until</code> 防止多 Relay 并发同一行；
 *       过期可被其他发布器接管（<code>idx_outbox_lease</code>）。</li>
 * </ul>
 * </p>
 * <p>
 * 状态机（建议）：
 * <code>PENDING → PUBLISHING → PUBLISHED</code>；失败进入 <code>FAILED</code>，
 * 根据退避计算 <code>next_retry_at</code> 重新回到 <code>PENDING</code> 或在策略判定后标记为 <code>DEAD</code>。
 * </p>
 * <p>
 * 字段说明：JSON 列（<code>payload_json</code>/<code>headers_json</code>）使用
 * {@link com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler JacksonTypeHandler} 与 Jackson {@link com.fasterxml.jackson.databind.JsonNode JsonNode}
 * 进行无模式（schemaless）存储；仅放置<strong>最小必要</strong>信息（如 taskId/sliceKey/planKey/provenance/operation/endpoint/priority/notBefore 等），
 * 体积较大的原始内容不得入队。
 * </p>
 * <p>
 * 审计与通用字段（如 <code>created_at</code>/<code>version</code> 等）继承自 {@link com.patra.starter.mybatis.entity.BaseDO.BaseDO BaseDO}。
 * </p>
 * <p>分层定位：Hexagonal 的 <em>infra / persistence DO</em>，仅承担持久化映射，不包含领域行为。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_outbox_message", autoResultMap = true)
public class OutboxMessageDO extends BaseDO {

    /**
     * 聚合类型（如：TASK/PLAN/...）。
     * <p>用途：审计与回放定位；帮助下游快速按聚合家族做度量或分流。</p>
     * <p>约束：NOT NULL；建议取值来源于受控字典，避免自由拼写。</p>
     */
    @TableField("aggregate_type")
    private String aggregateType;

    /**
     * 聚合根 ID。
     * <p>任务场景：等于 <code>ing_task.id</code>；其他聚合场景与各自主键保持一致。</p>
     * <p>用途：回放/对账/排障时的主关联键。</p>
     */
    @TableField("aggregate_id")
    private Long aggregateId;

    /**
     * 逻辑通道 = 目标 Topic（例如：<code>ingest.task</code>）。
     * <p>与 {@link #dedupKey} 组成唯一键保障幂等（UK：uk_outbox_channel_dedup）。</p>
     * <p>建议：通道种类保持稳定且数量可控，由适配层进行路由与鉴权。</p>
     */
    @TableField("channel")
    private String channel;

    /**
     * 业务语义标签（Operation Type），如：<code>TASK_READY</code>、<code>EVENT_PUBLISHED</code> 等。
     * <p>用途：供订阅方做业务分流/指标聚合；不参与幂等键计算。</p>
     */
    @TableField("op_type")
    private String opType;

    /**
     * 分片/顺序路由键。
     * <p>建议格式：<code>"provenance:operation"</code>，例如 <code>PUBMED:HARVEST</code>。</p>
     * <p>用途：在相同 <code>channel</code> 下控制分区并发与<strong>保序</strong>发布；
     * 命中索引 <code>idx_outbox_partition(channel, partition_key, status_code)</code>。</p>
     * <p>注意：不参与唯一去重；与 {@link #dedupKey} 语义独立。</p>
     */
    @TableField("partition_key")
    private String partitionKey;

    /**
     * 幂等键（Dedup Key）。
     * <p>唯一性约束：在同一 {@link #channel} 下必须唯一（UK：uk_outbox_channel_dedup）。</p>
     * <p>任务场景：建议等于 <code>ing_task.idempotent_key</code>；其他场景可采用 <code>requestId</code> 或内容哈希。</p>
     * <p>作用：源端去重与安全重试；消费者即便重复投递也能在源端屏蔽。</p>
     */
    @TableField("dedup_key")
    private String dedupKey;

    /**
     * 最小必要载荷（JSON）。
     * <p>典型字段：<code>taskId</code>/<code>sliceKey</code>/<code>planKey</code>/<code>provenance</code>/<code>operation</code>/<code>endpoint</code>/<code>priority</code>/<code>notBefore</code> 等。</p>
     * <p>约束：仅承载业务发布所需的<strong>精简</strong>信息；大字段/原始文档不得入队。</p>
     */
    @TableField(value = "payload_json", typeHandler = JacksonTypeHandler.class)
    private JsonNode payloadJson;

    /**
     * 扩展头（JSON），例如 <code>correlationId</code>、追踪上下文等。
     * <p>可空；用于跨系统链路对齐与排障。</p>
     */
    @TableField(value = "headers_json", typeHandler = JacksonTypeHandler.class)
    private JsonNode headersJson;

    /**
     * 最早可发布时间（UTC）。
     * <p>NULL = 随时可发；用于定时/延时发布与速率整形。</p>
     */
    @TableField("not_before")
    private Instant notBefore;

    /**
     * 发布状态码。
     * <p>取值集合：<code>PENDING</code>/<code>PUBLISHING</code>/<code>PUBLISHED</code>/<code>FAILED</code>/<code>DEAD</code>。</p>
     * <p>扫描策略：通常按 (<code>status_code</code>, <code>not_before</code>, <code>id</code>) 走索引批量提取。</p>
     */
    @TableField("status_code")
    private String statusCode;

    /**
     * 发布重试次数（失败则 +1）。
     * <p>可配合退避策略（指数/斐波那契等）计算 {@link #nextRetryAt}。</p>
     */
    @TableField("retry_count")
    private Integer retryCount;

    /**
     * 下次尝试发布时间（UTC）。
     * <p>当 <code>now &gt;= next_retry_at</code> 且状态允许时可被 Relay 重新提取。</p>
     */
    @TableField("next_retry_at")
    private Instant nextRetryAt;

    /**
     * 最近一次发布错误码（由 MQ SDK 或内部策略给出）。
     */
    @TableField("error_code")
    private String errorCode;

    /**
     * 最近一次发布错误详情（截断存储）。
     */
    @TableField("error_msg")
    private String errorMsg;

    /**
     * 发布器租约持有者（实例 ID / workerId）。
     * <p>用途：多 Relay 并行时避免同一记录重复发布。</p>
     */
    @TableField("pub_lease_owner")
    private String pubLeaseOwner;

    /**
     * 发布器租约到期时间（UTC）。
     * <p>到期前仅允许租约持有者处理；过期后可被其他发布器接管。</p>
     */
    @TableField("pub_leased_until")
    private Instant pubLeasedUntil;

    /**
     * Broker 返回的消息 ID（用于对账 / 回放标识）。
     */
    @TableField("msg_id")
    private String msgId;
}
