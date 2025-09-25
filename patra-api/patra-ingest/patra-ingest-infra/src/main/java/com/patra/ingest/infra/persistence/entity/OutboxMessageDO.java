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
 * Outbox 消息 DO - 对应 ing_outbox_message 表。
 * 六边形: infra / 持久化 DO
 * 语义: 与业务写入同事务落库，由 Relay 扫描并投递到 MQ；(channel, dedup_key) 唯一去重。
 *
 * @author linq……
 * @since 0.1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_outbox_message", autoResultMap = true)
public class OutboxMessageDO extends BaseDO {

    /** 聚合类型：如 TASK/PLAN/... */
    @TableField("aggregate_type")
    private String aggregateType;

    /** 聚合根ID；任务场景=ing_task.id */
    @TableField("aggregate_id")
    private Long aggregateId;

    /** 逻辑通道=目标Topic，如 ingest.task */
    @TableField("channel")
    private String channel;

    /** 业务语义标签：如 TASK_READY / EVENT_PUBLISHED */
    @TableField("op_type")
    private String opType;

    /** 分片/顺序路由键；建议 "provenance:operation" */
    @TableField("partition_key")
    private String partitionKey;

    /** 幂等键；(channel, dedup_key) 唯一 */
    @TableField("dedup_key")
    private String dedupKey;

    /** 最小必要载荷(JSON) */
    @TableField(value = "payload_json", typeHandler = JacksonTypeHandler.class)
    private JsonNode payloadJson;

    /** 扩展头(JSON) */
    @TableField(value = "headers_json", typeHandler = JacksonTypeHandler.class)
    private JsonNode headersJson;

    /** 最早可发布时间(UTC)：NULL=随时可发 */
    @TableField("not_before")
    private Instant notBefore;

    /** 发布状态：PENDING/PUBLISHING/PUBLISHED/FAILED/DEAD */
    @TableField("status_code")
    private String statusCode;

    /** 发布重试次数（失败则+1） */
    @TableField("retry_count")
    private Integer retryCount;

    /** 下次尝试发布时间(UTC) */
    @TableField("next_retry_at")
    private Instant nextRetryAt;

    /** 最近一次发布错误码 */
    @TableField("error_code")
    private String errorCode;

    /** 最近一次发布错误详情 */
    @TableField("error_msg")
    private String errorMsg;

    /** 发布器租约持有者 */
    @TableField("pub_lease_owner")
    private String pubLeaseOwner;

    /** 发布器租约到期(UTC) */
    @TableField("pub_leased_until")
    private Instant pubLeasedUntil;

    /** Broker 返回的消息ID（对账/回放标识） */
    @TableField("msg_id")
    private String msgId;
}
