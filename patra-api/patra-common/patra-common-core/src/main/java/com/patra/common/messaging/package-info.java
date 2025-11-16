/**
 * 消息通道包 - 事件总线路由和消息订阅。
 *
 * <p>本包提供消息通道键标识符,用于事件总线路由和消息订阅。通道键遵循三部分命名约定, 提供一致的命名方案,与特定消息传递实现解耦。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>定义消息通道键接口({@link com.patra.common.messaging.ChannelKey})
 *   <li>提供三部分命名约定(domain_resource_event)
 *   <li>为发布者和消费者提供一致的命名方案
 *   <li>与特定消息传递实现解耦(RocketMQ、Kafka、RabbitMQ 等)
 *   <li>支持领域事件路由和消息订阅
 * </ul>
 *
 * <h2>核心接口</h2>
 *
 * <ul>
 *   <li>{@link com.patra.common.messaging.ChannelKey} - 消息通道键接口, 定义三部分命名约定(domain、resource、event)
 * </ul>
 *
 * <h2>三部分命名约定</h2>
 *
 * <p>通道键由三部分组成,使用下划线连接,全大写:
 *
 * <ul>
 *   <li><strong>domain</strong>: 业务领域段(如 {@code ingest}、{@code registry}、{@code analysis})
 *   <li><strong>resource</strong>: 资源或聚合段(如 {@code task}、{@code article}、{@code plan})
 *   <li><strong>event</strong>: 事件段(如 {@code ready}、{@code created}、{@code updated})
 * </ul>
 *
 * <p>最终格式: {@code DOMAIN_RESOURCE_EVENT}(如 {@code INGEST_TASK_READY})
 *
 * <h2>使用场景</h2>
 *
 * <ul>
 *   <li><strong>事件发布</strong>: 发布者使用通道键标识消息主题
 *   <li><strong>事件订阅</strong>: 消费者使用通道键订阅感兴趣的消息
 *   <li><strong>路由规则</strong>: 根据通道键配置消息路由规则
 *   <li><strong>监控跟踪</strong>: 根据通道键统计消息流量和错误
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * // 1. 定义通道键枚举(在 API 模块中)
 * public enum IngestChannels implements ChannelKey {
 *     TASK_READY("ingest", "task", "ready"),
 *     TASK_COMPLETED("ingest", "task", "completed"),
 *     PUBLICATION_INGESTED("ingest", "publication", "ingested");
 *
 *     private final String domain;
 *     private final String resource;
 *     private final String event;
 *
 *     IngestChannels(String domain, String resource, String event) {
 *         this.domain = domain;
 *         this.resource = resource;
 *         this.event = event;
 *     }
 *
 *     @Override
 *     public String domain() { return domain; }
 *
 *     @Override
 *     public String resource() { return resource; }
 *
 *     @Override
 *     public String event() { return event; }
 * }
 *
 * // 2. 发布事件
 * String channelKey = IngestChannels.TASK_READY.channel();  // "INGEST_TASK_READY"
 * eventPublisher.publish(channelKey, taskReadyEvent);
 *
 * // 3. 订阅事件
 * @RocketMQMessageListener(
 *     topic = "INGEST_TASK_READY",
 *     consumerGroup = "publication-processor-group"
 * )
 * public class TaskReadyListener implements RocketMQListener<TaskReadyEvent> {
 *     @Override
 *     public void onMessage(TaskReadyEvent event) {
 *         // 处理任务就绪事件
 *     }
 * }
 * }</pre>
 *
 * <h2>命名规范</h2>
 *
 * <ul>
 *   <li><strong>domain</strong>: 使用与服务边界对齐的小写名称(如 {@code ingest}、{@code registry})
 *   <li><strong>resource</strong>: 使用与核心聚合或业务对象关联的小写名称(如 {@code task}、{@code article})
 *   <li><strong>event</strong>: 使用描述事实的小写过去式动词(如 {@code ready}、{@code created}、{@code updated})
 * </ul>
 *
 * <h2>RocketMQ 集成</h2>
 *
 * <p>通道键直接映射到 RocketMQ 主题名称:
 *
 * <ul>
 *   <li><strong>Topic</strong>: 通道键即为 RocketMQ Topic 名称(如 {@code INGEST_TASK_READY})
 *   <li><strong>Tag</strong>: 可选,用于进一步细分消息(如 {@code HIGH_PRIORITY})
 *   <li><strong>Consumer Group</strong>: 消费者组名称,根据业务需求定义
 * </ul>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><strong>一致性</strong>: 统一的命名约定,便于理解和维护
 *   <li><strong>解耦</strong>: 与具体消息中间件实现解耦,易于切换
 *   <li><strong>可读性</strong>: 清晰的三部分结构,易于识别消息类型
 *   <li><strong>可扩展</strong>: 支持自定义实现,满足不同业务需求
 * </ul>
 *
 * <h2>事件驱动架构</h2>
 *
 * <p>通道键是事件驱动架构的核心:
 *
 * <ul>
 *   <li><strong>发布订阅</strong>: 发布者和订阅者通过通道键解耦
 *   <li><strong>异步通信</strong>: 聚合间通过领域事件异步通信
 *   <li><strong>最终一致性</strong>: 支持最终一致性的分布式事务
 *   <li><strong>可观测性</strong>: 通过通道键跟踪消息流转
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.common.messaging;
