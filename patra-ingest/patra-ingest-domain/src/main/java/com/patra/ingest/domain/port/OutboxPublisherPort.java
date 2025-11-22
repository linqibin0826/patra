package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.vo.relay.RelayPlan;

/// Outbox 消息发布端口(六边形架构 - Domain → Infrastructure)。
/// 
/// **职责**: 发布 Outbox 消息到下游通道(MQ、webhook、S3 等),实现可靠消息投递。
/// 
/// **端口语义**: 此接口是六边形架构中的 **输出端口(Output Port)**,定义在 Domain
/// 层,由基础设施层(Infrastructure)实现,抽象底层通道细节。
/// 
/// @author linqibin
/// @since 0.1.0
public interface OutboxPublisherPort {

  /// 发布单条 Outbox 消息。
/// 
/// **业务含义**: 将消息发送到配置的下游通道。
/// 
/// @param message Outbox 实体,包括负载、头部、重试计数器
/// @param plan 转发计划,描述重试策略和租约上下文
/// @throws Exception 发布错误信号;调用方决定是否重试或失败
  void publish(OutboxMessage message, RelayPlan plan) throws Exception;
}
