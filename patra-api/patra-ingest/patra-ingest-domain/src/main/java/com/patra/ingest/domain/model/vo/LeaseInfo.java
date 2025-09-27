package com.patra.ingest.domain.model.vo;

import java.time.Instant;

/**
 * 任务租约信息值对象，封装租约持有者与续约次数。
 */
public record LeaseInfo(String owner, Instant leasedUntil, int leaseCount) {

    public LeaseInfo {
        if (leaseCount < 0) {
            throw new IllegalArgumentException("leaseCount 不能为负数");
        }
    }

    public static LeaseInfo none() {
        return new LeaseInfo(null, null, 0);
    }

    public static LeaseInfo snapshotOf(String owner, Instant leasedUntil, Integer leaseCount) {
        return new LeaseInfo(owner, leasedUntil, leaseCount == null ? 0 : leaseCount);
    }

    public boolean isHeld() {
        return owner != null && !owner.isBlank();
    }

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

    public LeaseInfo release() {
        if (!isHeld()) {
            return this;
        }
        return new LeaseInfo(null, null, leaseCount);
    }
}
