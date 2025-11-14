/**
 * 领域层基础包 - DDD 聚合根和领域事件基类。
 *
 * <p>本包提供所有 Patra 微服务领域层的基础抽象,包括聚合根基类、只读聚合根和领域事件接口。 这些抽象遵循 DDD(领域驱动设计)战术模式,支持事件驱动架构和 CQRS 模式。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>提供聚合根抽象基类 ({@link com.patra.common.domain.AggregateRoot})
 *   <li>提供只读聚合根基类({@link com.patra.common.domain.ReadOnlyAggregate})
 *   <li>定义领域事件标记接口({@link com.patra.common.domain.DomainEvent})
 *   <li>支持领域事件收集和发布(事件溯源模式)
 *   <li>提供乐观锁版本管理
 *   <li>确保领域层的框架无关性(纯 Java 实现)
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.common.domain.AggregateRoot} - 聚合根抽象基类, 支持领域事件收集、乐观锁版本管理和聚合标识符生命周期管理
 *   <li>{@link com.patra.common.domain.ReadOnlyAggregate} - 只读聚合根基类, 用于 CQRS 读端,不支持领域事件和状态修改
 *   <li>{@link com.patra.common.domain.DomainEvent} - 领域事件标记接口, 用于标识领域内发生的重要状态变更
 * </ul>
 *
 * <h2>AggregateRoot 核心特性</h2>
 *
 * <ul>
 *   <li><strong>领域事件收集</strong>: 通过 {@code addDomainEvent()} 收集事件, 通过 {@code pullDomainEvents()}
 *       提取事件(事件溯源模式)
 *   <li><strong>乐观锁支持</strong>: 通过 {@code version} 字段支持并发控制
 *   <li><strong>标识符管理</strong>: 通过 {@code assignId()} 分配聚合标识符
 *   <li><strong>不变量检查</strong>: 通过 {@code assertInvariants()} 钩子方法验证业务规则
 *   <li><strong>瞬态检测</strong>: 通过 {@code isTransient()} 判断是否已持久化
 * </ul>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><strong>框架无关</strong>: 仅依赖 JDK,不依赖 Spring、JPA 等框架
 *   <li><strong>事件驱动</strong>: 支持领域事件收集和发布,解耦聚合间通信
 *   <li><strong>一致性边界</strong>: 聚合根确保聚合内对象的一致性
 *   <li><strong>仓储模式</strong>: 通过仓储接口持久化和重建聚合根
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * // 1. 定义聚合根
 * public class Literature extends AggregateRoot<LiteratureId> {
 *     private String title;
 *     private PublicationStatus status;
 *
 *     // 业务方法触发状态变更和领域事件
 *     public void publish() {
 *         this.status = PublicationStatus.PUBLISHED;
 *         addDomainEvent(new LiteraturePublishedEvent(getId(), Instant.now()));
 *         assertInvariants();  // 验证业务规则
 *     }
 *
 *     // 不变量检查钩子
 *     @Override
 *     protected void assertInvariants() {
 *         if (status == PublicationStatus.PUBLISHED && title == null) {
 *             throw new IllegalStateException("已发布文献必须有标题");
 *         }
 *     }
 * }
 *
 * // 2. 定义领域事件
 * public record LiteraturePublishedEvent(LiteratureId id, Instant publishedAt)
 *     implements DomainEvent {}
 *
 * // 3. 应用层收集和发布事件
 * public class LiteratureApplicationService {
 *     public void publishLiterature(LiteratureId id) {
 *         Literature literature = repository.findById(id);
 *         literature.publish();  // 触发状态变更和事件收集
 *         repository.save(literature);  // 持久化
 *
 *         // 提取事件并发布(Outbox 模式)
 *         List<DomainEvent> events = literature.pullDomainEvents();
 *         eventPublisher.publish(events);
 *     }
 * }
 * }</pre>
 *
 * <h2>事件驱动架构集成</h2>
 *
 * <p>领域事件的生命周期:
 *
 * <ol>
 *   <li><strong>收集阶段</strong>: 聚合根通过 {@code addDomainEvent()} 收集事件
 *   <li><strong>持久化阶段</strong>: 应用层保存聚合根到仓储
 *   <li><strong>提取阶段</strong>: 应用层通过 {@code pullDomainEvents()} 提取事件
 *   <li><strong>发布阶段</strong>: 应用层通过 Outbox 模式或消息总线发布事件
 *   <li><strong>消费阶段</strong>: 其他聚合或服务订阅并消费事件
 * </ol>
 *
 * <h2>CQRS 模式支持</h2>
 *
 * <ul>
 *   <li><strong>写模型</strong>: {@link com.patra.common.domain.AggregateRoot}, 包含业务逻辑和状态修改,支持领域事件收集
 *   <li><strong>读模型</strong>: {@link com.patra.common.domain.ReadOnlyAggregate},
 *       只读聚合,用于查询优化,不支持状态修改
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.common.domain;
