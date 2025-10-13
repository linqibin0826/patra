package com.patra.ingest.domain.model.vo;

import java.time.Instant;

/**
 * Value object describing the lease assigned to a task.
 *
 * <p>Tracks the current holder and expiration for distributed execution, supporting
 * acquire/renew/release flows.
 *
 * <ul>
 *   <li>{@code owner}: lease holder (instance/node identifier)
 *   <li>{@code leasedUntil}: lease expiration in UTC
 *   <li>{@code leaseCount}: number of acquisitions/renewals (>= 0)
 * </ul>
 *
 * Invariant: {@code leaseCount} must not be negative.
 */
public record LeaseInfo(String owner, Instant leasedUntil, int leaseCount) {

  public LeaseInfo {
    if (leaseCount < 0) {
      throw new IllegalArgumentException("leaseCount must not be negative");
    }
  }

  /** Lease information for an unassigned task (no owner, no expiry, zero count). */
  public static LeaseInfo none() {
    return new LeaseInfo(null, null, 0);
  }

  /** Snapshot helper that normalizes a nullable {@code leaseCount} to zero. */
  public static LeaseInfo snapshotOf(String owner, Instant leasedUntil, Integer leaseCount) {
    return new LeaseInfo(owner, leasedUntil, leaseCount == null ? 0 : leaseCount);
  }

  /** Whether the lease currently has a holder. */
  public boolean isHeld() {
    return owner != null && !owner.isBlank();
  }

  /** Acquire the lease for the first time; requires the lease to be unheld. */
  public LeaseInfo acquire(String newOwner, Instant until) {
    if (newOwner == null || newOwner.isBlank()) {
      throw new IllegalArgumentException("Lease owner must not be blank");
    }
    if (until == null) {
      throw new IllegalArgumentException("Lease expiration must not be null");
    }
    if (isHeld()) {
      throw new IllegalStateException("Lease is already held and cannot be reacquired");
    }
    return new LeaseInfo(newOwner, until, leaseCount + 1);
  }

  /** Renew the lease; only the current holder may renew. */
  public LeaseInfo renew(String holder, Instant until) {
    if (!isHeld()) {
      throw new IllegalStateException("Cannot renew because no lease holder is present");
    }
    if (holder == null || holder.isBlank()) {
      throw new IllegalArgumentException("Lease holder used for renewal must not be blank");
    }
    if (!owner.equals(holder)) {
      throw new IllegalArgumentException("Lease holder mismatch; renewal rejected");
    }
    if (until == null) {
      throw new IllegalArgumentException("Lease expiration must not be null");
    }
    return new LeaseInfo(holder, until, leaseCount + 1);
  }

  /** Release the lease (returns {@code this} when already unheld). */
  public LeaseInfo release() {
    if (!isHeld()) {
      return this;
    }
    return new LeaseInfo(null, null, leaseCount);
  }
}
