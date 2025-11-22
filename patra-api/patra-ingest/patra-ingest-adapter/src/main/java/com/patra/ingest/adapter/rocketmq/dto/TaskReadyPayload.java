package com.patra.ingest.adapter.rocketmq.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/// 任务就绪消息负载对象。
/// 
/// 解析 RocketMQ INGEST_TASK_READY 主题的消息体(JSON 格式)。采用简化设计,仅包含任务执行的最小必需字段,其他业务数据从数据库查询以减少消息体积和耦合。
/// 
/// 字段说明:
/// 
/// - taskId: 任务 ID(必填) - 用于加载任务上下文和获取分布式租约
///   - idempotentKey: 幂等键(必填) - 用于消息去重和防止重复执行
/// 
/// 设计理由: 消息负载最小化避免了序列化大对象的开销,减少网络传输成本,并降低消息生产者与消费者之间的耦合度。
/// 
/// @author linqibin
/// @since 0.2.0
@Data
public class TaskReadyPayload {

  /// 任务 ID
  @JsonProperty("taskId")
  private Long taskId;

  /// 幂等键
  @JsonProperty("idempotentKey")
  private String idempotentKey;

  /// 验证必填字段。
/// 
/// @throws IllegalArgumentException 当必填字段为 null/空白时
  public void validate() {
    if (taskId == null) {
      throw new IllegalArgumentException("任务 ID 不能为空");
    }
    if (idempotentKey == null || idempotentKey.isBlank()) {
      throw new IllegalArgumentException("幂等键不能为空");
    }
  }
}
