package com.patra.common.domain;

import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * 只读聚合根抽象基类。
 * <p>
 * 专为 CQRS 查询侧设计的轻量级聚合根基类，提供基本的标识管理功能，
 * 但不包含领域事件、版本控制等写操作相关的复杂性。
 * </p>
 * 
 * <p>
 * 约束与约定：
 * - 仅依赖 JDK，domain 层零框架污染
 * - 专注于数据查询和业务规则验证
 * - 不支持状态变更和事件发布
 * - 适用于配置、字典、视图等只读聚合
 * </p>
 *
 * @param <ID> 聚合根ID类型（值对象或基本类型封装）
 * @author linqibin
 * @since 0.1.0
 */
public abstract class ReadOnlyAggregate<ID> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 聚合根标识。
     * -- GETTER --
     *  获取聚合根ID。
     */
    @Getter
    private final ID id;

    /**
     * 构造函数（带ID）。
     */
    protected ReadOnlyAggregate(ID id) {
        this.id = id;
    }

    /**
     * 构造函数（无ID，用于工厂方法中后续设置）。
     */
    protected ReadOnlyAggregate() {
        this.id = null;
    }

    /**
     * 是否为瞬态对象（无ID）。
     */
    public boolean isTransient() {
        return this.id == null;
    }

    /**
     * 领域不变量校验钩子：在构造或查询时调用，抛出 IllegalStateException 终止非法状态。
     * 子类可覆写并在需要时显式调用。
     */
    protected void assertInvariants() {
        // 默认空实现；子类自行校验（如数据完整性、业务规则等）
    }

    /**
     * 基于ID的相等性比较。
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        ReadOnlyAggregate<?> that = (ReadOnlyAggregate<?>) obj;
        return Objects.equals(id, that.id);
    }

    /**
     * 基于ID的哈希码。
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * 字符串表示。
     */
    @Override
    public String toString() {
        return String.format("%s{id=%s}", getClass().getSimpleName(), id);
    }
}
