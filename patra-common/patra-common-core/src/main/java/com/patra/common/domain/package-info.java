/// 领域层基础包 - DDD 聚合根和领域事件基类。
///
/// 本包提供所有 Patra 微服务领域层的基础抽象,包括聚合根基类、只读聚合根和领域事件接口。 这些抽象遵循 DDD(领域驱动设计)战术模式,支持事件驱动架构和 CQRS 模式。
///
/// ## 职责
///
/// - 提供聚合根抽象基类 ({@link com.patra.common.domain.AggregateRoot})
///   - 提供只读聚合根基类({@link com.patra.common.domain.ReadOnlyAggregate})
///   - 定义领域事件标记接口({@link com.patra.common.domain.DomainEvent})
///   - 支持领域事件收集和发布(事件溯源模式)
///   - 提供乐观锁版本管理
///   - 确保领域层的框架无关性(纯 Java 实现)
///
/// ## 核心组件
///
/// - {@link com.patra.common.domain.AggregateRoot} - 聚合根抽象基类, 支持领域事件收集、乐观锁版本管理和聚合标识符生命周期管理
///   - {@link com.patra.common.domain.ReadOnlyAggregate} - 只读聚合根基类, 用于 CQRS 读端,不支持领域事件和状态修改
///   - {@link com.patra.common.domain.DomainEvent} - 领域事件标记接口, 用于标识领域内发生的重要状态变更
///
/// ## AggregateRoot 核心特性
///
/// - **领域事件收集**: 通过 `addDomainEvent()` 收集事件, 通过 `pullDomainEvents()`
///       提取事件(事件溯源模式)
///   - **乐观锁支持**: 通过 `version` 字段支持并发控制
///   - **标识符管理**: 通过 `assignId()` 分配聚合标识符
///   - **不变量检查**: 通过 `assertInvariants()` 钩子方法验证业务规则
///   - **瞬态检测**: 通过 `isTransient()` 判断是否已持久化
///
/// ## 设计原则
///
/// - **框架无关**: 仅依赖 JDK,不依赖 Spring、JPA 等框架
///   - **事件驱动**: 支持领域事件收集和发布,解耦聚合间通信
///   - **一致性边界**: 聚合根确保聚合内对象的一致性
///   - **仓储模式**: 通过仓储接口持久化和重建聚合根
///
/// ## 使用示例
///
/// ```java
/// // 1. 定义聚合根
/// public class Publication extends AggregateRoot<PublicationId> {
///     private String title;
///     private PublicationStatus status;
///
///     // 业务方法触发状态变更和领域事件
///     public void publish() {
///         this.status = PublicationStatus.PUBLISHED;
///         addDomainEvent(new PublicationPublishedEvent(getId(), Instant.now()));
///         assertInvariants();  // 验证业务规则
///
///     // 不变量检查钩子
///     @Override
///     protected void assertInvariants() {
///         if (status == PublicationStatus.PUBLISHED && title == null) {
///             throw new IllegalStateException("已发布出版物必须有标题");
///
/// // 2. 定义领域事件
/// public record PublicationPublishedEvent(PublicationId id, Instant publishedAt)
///     implements DomainEvent {
///
/// // 3. 应用层收集和发布事件
/// public class PublicationApplicationService {
///     public void publishPublication(PublicationId id) {
///         Publication publication = repository.findById(id);
///         publication.publish();  // 触发状态变更和事件收集
///         repository.save(publication);  // 持久化
///
///         // 提取事件并发布(Outbox 模式)
///         List<DomainEvent> events = publication.pullDomainEvents();
///         eventPublisher.publish(events);
/// ```
///
/// ## 事件驱动架构集成
///
/// 领域事件的生命周期:
///
/// ## CQRS 模式支持
///
/// - **写模型**: {@link com.patra.common.domain.AggregateRoot}, 包含业务逻辑和状态修改,支持领域事件收集
///   - **读模型**: {@link com.patra.common.domain.ReadOnlyAggregate},
///       只读聚合,用于查询优化,不支持状态修改
///
/// @since 0.1.0
/// @author linqibin
package com.patra.common.domain;
