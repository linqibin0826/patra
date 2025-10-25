package com.patra.ingest.domain.model.vo;

import java.time.Instant;

/**
 * Value object describing the lease assigned to a task.
 *
 * <p>Tracks the current holder and expiration for distributed execution, supporting
 * acquire/renew/release flows.
 *
 * @param owner lease holder (instance/node identifier)
 * @param leasedUntil lease expiration in UTC
 * @param leaseCount number of acquisitions/renewals (must be >= 0)
 */
public record LeaseInfo(String owner, Instant leasedUntil, int leaseCount) {

  public LeaseInfo {
    if (leaseCount < 0) {
      throw new IllegalArgumentException("leaseCount must not be negative");
    }
  }

  /**
   * Creates lease information for an unassigned task.
   *
   * @return empty lease with no owner, no expiry, and zero count
   */
  public static LeaseInfo none() {
    return new LeaseInfo(null, null, 0);
  }

  /**
   * Creates lease information from nullable lease count.
   *
   * @param owner lease owner
   * @param leasedUntil lease expiration
   * @param leaseCount lease count (null treated as zero)
   * @return lease information with normalized count
   */
  public static LeaseInfo snapshotOf(String owner, Instant leasedUntil, Integer leaseCount) {
    return new LeaseInfo(owner, leasedUntil, leaseCount == null ? 0 : leaseCount);
  }

  /**
   * Checks whether the lease currently has a holder.
   *
   * @return true if lease has a non-blank owner
   */
  public boolean isHeld() {
    return owner != null && !owner.isBlank();
  }

  /**
   * Acquires the lease for the first time.
   *
   * @param newOwner new lease owner identifier
   * @param until lease expiration time
   * @return new lease information with incremented count
   * @throws IllegalArgumentException if newOwner or until is null/blank
   * @throws IllegalStateException if lease is already held
   */
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

  /**
   * Renews the existing lease.
   *
   * @param holder current lease holder identifier
   * @param until new lease expiration time
   * @return new lease information with incremented count
   * @throws IllegalStateException if no lease is currently held
   * @throws IllegalArgumentException if holder is null/blank, null until, or holder mismatch
   */
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

  /**
   * Releases the lease.
   *
   * @return new lease information with cleared owner and expiration (returns this if already
   *     unheld)
   */
  public LeaseInfo release() {
    if (!isHeld()) {
      return this;
    }
    return new LeaseInfo(null, null, leaseCount);
  }
}
