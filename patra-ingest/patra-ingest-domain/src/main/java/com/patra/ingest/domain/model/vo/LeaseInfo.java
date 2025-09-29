package com.patra.ingest.domain.model.vo;

import java.time.Instant;

/**
 * 任务租约信息（Lease Info）。
 * <p>用于分布式竞争执行时标记当前持有者与到期时间，支持获取 / 续约 / 释放。</p>
 * <ul>
 *   <li>owner：租约持有者（实例/节点标识）</li>
 *   <li>leasedUntil：租约到期时间（UTC）</li>
 *   <li>leaseCount：累计获取/续约次数（>=0）</li>
 * </ul>
 * 不变式：leaseCount 不可为负。
 */
public record LeaseInfo(String owner, Instant leasedUntil, int leaseCount) {

    public LeaseInfo {
        if (leaseCount < 0) {
            throw new IllegalArgumentException("leaseCount 不能为负数");
        }
    }

    /**
     * 无租约实例（空持有者 / 到期 / 次数 0）。
     */
    public static LeaseInfo none() {
        return new LeaseInfo(null, null, 0);
    }

    /**
     * 快照构造：将可能为 null 的 leaseCount 归一化为 0。
     */
    public static LeaseInfo snapshotOf(String owner, Instant leasedUntil, Integer leaseCount) {
        return new LeaseInfo(owner, leasedUntil, leaseCount == null ? 0 : leaseCount);
    }

    /**
     * 是否已有持有者。
     */
    public boolean isHeld() {
        return owner != null && !owner.isBlank();
    }

    /**
     * 首次获取租约（当前必须未被持有）。
     */
    public LeaseInfo acquire(String newOwner, Instant until) {
        if (newOwner == null || newOwner.isBlank()) {
            throw new IllegalArgumentException("租约持有者不能为空");
        }
        if (until == null) {
            throw new IllegalArgumentException("租约到期时间不能为空");
        }
        if (isHeld()) {
            throw new IllegalStateException("租约已被占用，无法重新获取");
        }
        return new LeaseInfo(newOwner, until, leaseCount + 1);
    }

    /**
     * 续约（仅原持有者可续约）。
     */
    public LeaseInfo renew(String holder, Instant until) {
        if (!isHeld()) {
            throw new IllegalStateException("当前无租约持有者，无法续约");
        }
        if (holder == null || holder.isBlank()) {
            throw new IllegalArgumentException("续约的租约持有者不能为空");
        }
        if (!owner.equals(holder)) {
            throw new IllegalArgumentException("租约持有者不匹配，拒绝续约");
        }
        if (until == null) {
            throw new IllegalArgumentException("租约到期时间不能为空");
        }
        return new LeaseInfo(holder, until, leaseCount + 1);
    }

    /**
     * 释放租约（无持有者时返回自身）。
     */
    public LeaseInfo release() {
        if (!isHeld()) {
            return this;
        }
        return new LeaseInfo(null, null, leaseCount);
    }
}
