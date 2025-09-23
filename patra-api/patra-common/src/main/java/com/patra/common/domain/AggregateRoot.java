package com.patra.common.domain;

import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 通用聚合根抽象基类。
 * <p>
 * 约束与约定：
 * - 仅依赖 JDK，domain 层零框架污染。
 * - 外部只能通过聚合根暴露的方法改变聚合内部状态，保持不变量。
 * - 领域事件只挂在聚合根上，由应用层拉取并转发到 Outbox/消息总线。
 *
 * @param <ID> 聚合根ID类型（值对象或基本类型封装）
 */
public abstract class AggregateRoot<ID> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 聚合根标识。由仓储在首次持久化时产生或赋值。
     * -- GETTER --
     *  获取聚合根ID（可能为 null，表示尚未持久化）。

     */
    @Getter
    private ID id;

    /**
     * 乐观锁版本（可选）：由基础设施层在持久化时维护；domain 可只读或参与简单校验。
     * -- GETTER --
     *  获取乐观锁版本。

     */
    @Getter
    private long version;

    /**
     * 暂存的领域事件，等待应用层拉取并发布。
     */
    private final transient List<DomainEvent> domainEvents = new ArrayList<>();

    protected AggregateRoot() {
    }

    protected AggregateRoot(ID id) {
        this.id = id;
    }

    /**
     * 仅供仓储在重建或首次保存时设置 ID。应用代码不要调用。
     */
    public void assignId(ID id) {
        this.id = Objects.requireNonNull(id, "aggregate id must not be null");
    }

    /**
     * 仅供基础设施层在持久化时回填版本。
     */
    public void assignVersion(long version) {
        if (version < 0) {
            throw new IllegalArgumentException("version must be >= 0");
        }
        this.version = version;
    }

    /**
     * 是否为瞬态对象（未持久化）。
     */
    public boolean isTransient() {
        return this.id == null;
    }

    /**
     * 聚合根添加领域事件（由聚合内部行为在完成状态变更后调用）。
     */
    protected void addDomainEvent(DomainEvent event) {
        if (event == null) return;
        domainEvents.add(event);
    }

    /**
     * 拉取并清空事件（应用层在事务边界内调用，随后发布到 Outbox）。
     * 一次性取走，保证不会重复发布。
     */
    public List<DomainEvent> pullDomainEvents() {
        if (domainEvents.isEmpty()) {
            return Collections.emptyList();
        }
        List<DomainEvent> snapshot = List.copyOf(domainEvents);
        domainEvents.clear();
        return snapshot;
    }

    /**
     * 只读查看事件（调试或测试用）。
     */
    public List<DomainEvent> peekDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    /**
     * 领域不变量校验钩子：在关键状态变更后调用，抛出 IllegalStateException 终止非法状态。
     * 子类可覆写并在用例中显式调用，或在每个行为方法末尾调用。
     */
    protected void assertInvariants() {
        // 默认空实现；子类自行校验（如状态机合法性、值对象一致性等）
    }

    /* ========== 可选辅助：为事件赋默认发生时间 ========== */

    /**
     * 工具：如果事件没有发生时间，补上当前时间（子类可用）。
     */
    protected static Instant nowIfNull(Instant t) {
        return (t == null) ? Instant.now() : t;
    }
}
