/**
 * 业务消息常量定义包。
 *
 * <p>本包定义采集服务的业务消息通道和操作类型,遵循 DDD 和六边形架构原则。
 *
 * <p><strong>核心概念</strong>:
 *
 * <ul>
 *   <li>{@link com.patra.ingest.domain.messaging.MessageChannels} - 业务消息通道
 *   <li>{@link com.patra.ingest.domain.messaging.MessageOperations} - 业务消息操作
 * </ul>
 *
 * <p><strong>架构原则</strong>:
 *
 * <ul>
 *   <li>Domain 层使用纯业务语言,不包含技术框架概念(如 RocketMQ、Kafka 等)
 *   <li>技术映射(如 Channel → Topic)由基础设施层(Infra)负责
 *   <li>保持领域模型的技术无关性和可测试性
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
package com.patra.ingest.domain.messaging;
