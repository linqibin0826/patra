/// 消息通道包 - 事件总线路由和消息订阅。
///
/// 本包提供消息通道键标识符,用于事件总线路由和消息订阅。通道键遵循三部分命名约定, 提供一致的命名方案,与特定消息传递实现解耦。
///
/// ## 职责
///
/// - 定义消息通道键接口({@link com.patra.common.messaging.ChannelKey})
///   - 提供三部分命名约定(domain_resource_event)
///   - 为发布者和消费者提供一致的命名方案
///   - 与特定消息传递实现解耦(RocketMQ、Kafka、RabbitMQ 等)
///   - 支持领域事件路由和消息订阅
///
/// ## 核心接口
///
/// - {@link com.patra.common.messaging.ChannelKey} - 消息通道键接口, 定义三部分命名约定(domain、resource、event)
///
/// ## 三部分命名约定
///
/// 通道键由三部分组成,使用下划线连接,全大写:
///
/// - **domain**: 业务领域段(如 `ingest`、`registry`、`analysis`)
///   - **resource**: 资源或聚合段(如 `task`、`article`、`plan`)
///   - **event**: 事件段(如 `ready`、`created`、`updated`)
///
/// 最终格式: `DOMAIN_RESOURCE_EVENT`(如 `INGEST_TASK_READY`)
///
/// ## 使用场景
///
/// - **事件发布**: 发布者使用通道键标识消息主题
///   - **事件订阅**: 消费者使用通道键订阅感兴趣的消息
///   - **路由规则**: 根据通道键配置消息路由规则
///   - **监控跟踪**: 根据通道键统计消息流量和错误
///
/// ## 使用示例
///
/// ```java
/// // 1. 定义通道键枚举(在 API 模块中)
/// public enum IngestChannels implements ChannelKey {
///     TASK_READY("ingest", "task", "ready"),
///     TASK_COMPLETED("ingest", "task", "completed"),
///     PUBLICATION_INGESTED("ingest", "publication", "ingested");
///
///     private final String domain;
///     private final String resource;
///     private final String event;
///
///     IngestChannels(String domain, String resource, String event) {
///         this.domain = domain;
///         this.resource = resource;
///         this.event = event;
///
///     @Override
///     public String domain() { return domain;
///
///     @Override
///     public String resource() { return resource;
///
///     @Override
///     public String event() { return event;
///
/// // 2. 发布事件
/// String channelKey = IngestChannels.TASK_READY.channel();  // "INGEST_TASK_READY"
/// eventPublisher.publish(channelKey, taskReadyEvent);
///
/// // 3. 订阅事件
/// @RocketMQMessageListener(
///     topic = "INGEST_TASK_READY",
///     consumerGroup = "publication-processor-group"
/// )
/// public class TaskReadyListener implements RocketMQListener<TaskReadyEvent> {
///     @Override
///     public void onMessage(TaskReadyEvent event) {
///         // 处理任务就绪事件
/// ```
///
/// ## 命名规范
///
/// - **domain**: 使用与服务边界对齐的小写名称(如 `ingest`、`registry`)
///   - **resource**: 使用与核心聚合或业务对象关联的小写名称(如 `task`、`article`)
///   - **event**: 使用描述事实的小写过去式动词(如 `ready`、`created`、`updated`)
///
/// ## RocketMQ 集成
///
/// 通道键直接映射到 RocketMQ 主题名称:
///
/// - **Topic**: 通道键即为 RocketMQ Topic 名称(如 `INGEST_TASK_READY`)
///   - **Tag**: 可选,用于进一步细分消息(如 `HIGH_PRIORITY`)
///   - **Consumer Group**: 消费者组名称,根据业务需求定义
///
/// ## 设计原则
///
/// - **一致性**: 统一的命名约定,便于理解和维护
///   - **解耦**: 与具体消息中间件实现解耦,易于切换
///   - **可读性**: 清晰的三部分结构,易于识别消息类型
///   - **可扩展**: 支持自定义实现,满足不同业务需求
///
/// ## 事件驱动架构
///
/// 通道键是事件驱动架构的核心:
///
/// - **发布订阅**: 发布者和订阅者通过通道键解耦
///   - **异步通信**: 聚合间通过领域事件异步通信
///   - **最终一致性**: 支持最终一致性的分布式事务
///   - **可观测性**: 通过通道键跟踪消息流转
///
/// @since 0.1.0
/// @author linqibin
package com.patra.common.messaging;
