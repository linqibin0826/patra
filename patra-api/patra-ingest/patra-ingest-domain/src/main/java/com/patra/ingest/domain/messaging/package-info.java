/// 业务消息常量定义包。
/// 
/// 本包定义采集服务的业务消息通道和操作类型,遵循 DDD 和六边形架构原则。
/// 
/// **核心概念**:
/// 
/// - {@link com.patra.ingest.domain.messaging.IngestPublishingChannels} - 业务消息通道（资源级别）
///   - {@link com.patra.ingest.domain.messaging.OperationType} - 操作类型接口（操作级别）
/// 
/// **设计理念**:
/// 
/// - **资源级别 Channel**：一个资源一个 Topic（如 INGEST_TASK、INGEST_PUBLICATION）
///   - **操作级别 OperationType**：用 RocketMQ Tags 区分操作（如 READY、FAILED、DATA_READY）
///   - **关注点分离**：Channel 负责路由，OperationType 负责业务语义
/// 
/// **架构原则**:
/// 
/// - Domain 层使用纯业务语言,不包含技术框架概念(如 RocketMQ、Kafka 等)
///   - 技术映射(如 Channel → Topic)由基础设施层(Infra)负责
///   - 保持领域模型的技术无关性和可测试性
/// 
/// **使用示例**:
/// 
/// ```java
/// // 1. 定义消息通道（Domain 层）
/// IngestPublishingChannels channel = IngestPublishingChannels.TASK;
/// String channelKey = channel.channel(); // "INGEST_TASK"
/// 
/// // 2. 定义操作类型（App 层实现）
/// OperationType opType = TaskOperations.READY;
/// String opCode = opType.getCode(); // "READY"
/// 
/// // 3. 组合为 RocketMQ 目标（Infra 层映射）
/// // Destination = "INGEST_TASK:READY"
/// // Topic = "INGEST_TASK"
/// // Tags = "READY"
/// ```
/// 
/// @author linqibin
/// @since 0.2.0
package com.patra.ingest.domain.messaging;
