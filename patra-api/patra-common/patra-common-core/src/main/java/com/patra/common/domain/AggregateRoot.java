package com.patra.common.domain;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.Getter;

/**
 * 聚合根的抽象基类。
 *
 * <p>约束和约定:
 *
 * <ul>
 *   <li>仅依赖 JDK;领域层保持无框架依赖。
 *   <li>状态变更必须通过聚合行为发生以保持不变量。
 *   <li>领域事件附加到聚合上,由应用层拉取后发布(例如,通过 outbox 或消息总线)。
 * </ul>
 *
 * @param <ID> 聚合标识符类型(值对象或封装的原始类型)
 */
public abstract class AggregateRoot<ID> implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * 由仓储在首次持久化时分配的聚合标识符。
   *
   * <p>在聚合尚未持久化时,Getter 返回 {@code null}。
   */
  @Getter private ID id;

  /**
   * 由基础设施层维护的可选乐观锁版本。
   *
   * <p>主要用于领域内的只读检查。
   */
  @Getter private long version;

  /** 待应用层收集的挂起领域事件。 */
  private final transient List<DomainEvent> domainEvents = new ArrayList<>();

  protected AggregateRoot() {}

  protected AggregateRoot(ID id) {
    this.id = id;
  }

  /** 在重建或首次持久化时分配标识符。仅供仓储使用。 */
  public void assignId(ID id) {
    this.id = Objects.requireNonNull(id, "聚合 ID 不能为 null");
  }

  /** 设置乐观锁版本。基础设施层应在持久化更新时调用此方法。 */
  public void assignVersion(long version) {
    if (version < 0) {
      throw new IllegalArgumentException("版本必须 >= 0");
    }
    this.version = version;
  }

  /** 指示聚合是否尚未持久化。 */
  public boolean isTransient() {
    return this.id == null;
  }

  /** 注册由聚合行为在状态变更后产生的领域事件。 */
  protected void addDomainEvent(DomainEvent event) {
    if (event == null) return;
    domainEvents.add(event);
  }

  /** 提取并清空暂存的领域事件。应用层应在事务边界内调用此方法,然后再发布到 outbox。 */
  public List<DomainEvent> pullDomainEvents() {
    if (domainEvents.isEmpty()) {
      return Collections.emptyList();
    }
    List<DomainEvent> snapshot = List.copyOf(domainEvents);
    domainEvents.clear();
    return snapshot;
  }

  /** 返回暂存领域事件的不可变视图(用于调试或测试)。 */
  public List<DomainEvent> peekDomainEvents() {
    return Collections.unmodifiableList(domainEvents);
  }

  /** 领域不变量检查的钩子方法。覆盖此方法以在关键转换后验证状态, 并在不变量被违反时抛出 {@link IllegalStateException}。 */
  protected void assertInvariants() {
    // 默认为空操作;子类应强制执行不变量,如状态机有效性或值对象一致性。
  }

  /* ========== 为事件分配默认时间戳的可选辅助方法 ========== */

  /** 当事件时间戳缺失时提供当前时间。 */
  protected static Instant nowIfNull(Instant t) {
    return (t == null) ? Instant.now() : t;
  }
}
