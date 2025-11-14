/**
 * Outbox 核心抽象和发布逻辑包。
 *
 * <p>本包提供 Outbox 发布器的抽象基类和核心模型，定义通用发布流程和扩展点。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>定义 Outbox 发布器的模板方法（Template Method Pattern）
 *   <li>提供批量发布的通用逻辑
 *   <li>管理 Outbox 消息的生命周期（PENDING → PUBLISHED）
 *   <li>定义发布上下文和发布结果模型
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@code AbstractOutboxPublisher<E, P, H>} - Outbox 发布器抽象基类
 *       <ul>
 *         <li><strong>E</strong>: 领域事件类型（如 {@code TaskQueuedEvent}）
 *         <li><strong>P</strong>: 负载类型（如 {@code TaskPayload}，继承 {@code OutboxPayload}）
 *         <li><strong>H</strong>: 消息头类型（如 {@code TaskHeaders}，继承 {@code OutboxHeaders}）
 *       </ul>
 *   <li>{@code OutboxPublishContext} - 发布上下文
 *       <ul>
 *         <li>包含批次信息、租约信息、超时配置
 *       </ul>
 *   <li>{@code OutboxPublishResult} - 发布结果
 *       <ul>
 *         <li>包含成功数、失败数、错误信息
 *       </ul>
 * </ul>
 *
 * <h2>模板方法流程</h2>
 *
 * <pre>
 * AbstractOutboxPublisher.publish(List<E> events)
 *   ↓
 * 1. validateEvent(E event)              [可选覆盖]
 * 2. getAggregateId(E event)             [必须实现]
 * 3. buildPayload(E event) → P           [必须实现]
 * 4. buildHeaders(E event) → H           [必须实现]
 * 5. buildPartitionKey(E event)          [必须实现]
 * 6. buildDedupKey(E event)              [必须实现]
 * 7. resolveNotBefore(E event)           [可选覆盖，默认：Instant.now()]
 * 8. getAggregateType()                  [必须实现]
 * 9. getChannel()                        [必须实现]
 * 10. getOperationType()                 [必须实现]
 *   ↓
 * 构造 OutboxMessage 并保存到数据库
 *   ↓
 * 返回 OutboxPublishResult
 * </pre>
 *
 * <h2>扩展指南</h2>
 *
 * <h3>必须实现的方法</h3>
 *
 * <pre>{@code
 * @Component
 * public class MyOutboxPublisher
 *     extends AbstractOutboxPublisher<MyEvent, MyPayload, MyHeaders> {
 *
 *     // 1. 定义聚合类型
 *     @Override
 *     protected OutboxAggregateTypes getAggregateType() {
 *         return OutboxAggregateTypes.MY_AGGREGATE;
 *     }
 *
 *     // 2. 定义通道
 *     @Override
 *     protected OutboxChannels getChannel() {
 *         return OutboxChannels.MY_CHANNEL;
 *     }
 *
 *     // 3. 构建负载
 *     @Override
 *     protected MyPayload buildPayload(MyEvent event) {
 *         return new MyPayload(event.getData());
 *     }
 *
 *     // 4. 构建消息头
 *     @Override
 *     protected MyHeaders buildHeaders(MyEvent event) {
 *         return new MyHeaders(event.getMetadata());
 *     }
 *
 *     // 5. 定义分区策略
 *     @Override
 *     protected String buildPartitionKey(MyEvent event) {
 *         return String.valueOf(event.getEntityId());
 *     }
 *
 *     // 6. 定义幂等键
 *     @Override
 *     protected String buildDedupKey(MyEvent event) {
 *         return event.getEventId();
 *     }
 *
 *     // 7. 获取聚合 ID
 *     @Override
 *     protected String getAggregateId(MyEvent event) {
 *         return String.valueOf(event.getEntityId());
 *     }
 *
 *     // 8. 获取操作类型
 *     @Override
 *     protected OutboxBusinessTags getOperationType() {
 *         return OutboxBusinessTags.MY_OPERATION;
 *     }
 * }
 * }</pre>
 *
 * <h3>可选覆盖的方法</h3>
 *
 * <pre>{@code
 * // 自定义事件验证
 * @Override
 * protected void validateEvent(MyEvent event) {
 *     if (event.getData() == null) {
 *         throw new IllegalArgumentException("Data cannot be null");
 *     }
 * }
 *
 * // 延迟发布（如延迟 5 分钟）
 * @Override
 * protected Instant resolveNotBefore(MyEvent event) {
 *     return Instant.now().plus(Duration.ofMinutes(5));
 * }
 * }</pre>
 *
 * <h2>设计模式</h2>
 *
 * <ul>
 *   <li><strong>模板方法模式</strong>: {@code AbstractOutboxPublisher} 定义发布流程骨架
 *   <li><strong>策略模式</strong>: 子类定义具体的分区策略、幂等策略
 *   <li><strong>泛型设计</strong>: 支持不同事件、负载、消息头类型
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.ingest.app.outbox.core;
