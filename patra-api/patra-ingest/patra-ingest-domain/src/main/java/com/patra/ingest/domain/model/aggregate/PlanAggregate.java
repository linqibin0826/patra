package com.patra.ingest.domain.model.aggregate;

import com.patra.common.domain.AggregateRoot;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.PlanStatus;
import com.patra.ingest.domain.model.vo.WindowSpec;
import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate root that represents the blueprint of a single ingestion plan together with its state
 * transitions.
 *
 * <p>The aggregate captures the window specification, expression and configuration snapshots,
 * slicing strategy, and current status. Persistence responsibilities remain in the repository
 * layer.
 *
 * <p>Idempotency: the {@code planKey} (source + operation + window + strategy hash) is managed by
 * repositories to prevent duplicate plans.
 *
 * <p>State machine: {@code DRAFT → SLICING → READY/PARTIAL → COMPLETED/FAILED}. When a plan fails,
 * upper-layer compensation logic decides the follow-up action.
 *
 * <p>Thread safety: this aggregate is created and mutated within a single thread and must not be
 * shared across threads.
 *
 * @author linqibin
 * @since 0.1.0
 */
public class PlanAggregate extends AggregateRoot<Long> {

  /** Scheduler instance identifier associated with the external trigger. */
  private final Long scheduleInstanceId;

  /** Business idempotency key used for deduplication. */
  private final String planKey;

  /** Provenance/source code (for example: PUBMED). */
  private final String provenanceCode;

  /** Operation type (full, incremental, compensation, and so on). */
  private final OperationCode operationCode;

  /** Hash of the plan expression prototype, used for change detection. */
  private final String exprProtoHash;

  /** Snapshot of the raw expression prototype (JSON form, prior to compilation). */
  private final String exprProtoSnapshotJson;

  /** Snapshot of the provenance configuration captured for execution. */
  private final String provenanceConfigSnapshotJson;

  /** Hash of the provenance configuration snapshot for change detection. */
  private final String provenanceConfigHash;

  /**
   * Window boundary specification (supports TIME/DATE/ID_RANGE/CURSOR_LANDMARK/VOLUME_BUDGET/SINGLE
   * strategies).
   */
  private final WindowSpec windowSpec;

  /** Slicing strategy code (for example TIME, DATE, or SINGLE). */
  private final String sliceStrategyCode;

  /** JSON payload containing slicing strategy parameters. */
  private final String sliceParamsJson;

  /** Current state of the plan. */
  private PlanStatus status;

  private PlanAggregate(
      Long id,
      Long scheduleInstanceId,
      String planKey,
      String provenanceCode,
      OperationCode operationCode,
      String exprProtoHash,
      String exprProtoSnapshotJson,
      String provenanceConfigSnapshotJson,
      String provenanceConfigHash,
      WindowSpec windowSpec,
      String sliceStrategyCode,
      String sliceParamsJson,
      PlanStatus status) {
    super(id);
    this.scheduleInstanceId =
        Objects.requireNonNull(scheduleInstanceId, "scheduleInstanceId must not be null");
    this.planKey = Objects.requireNonNull(planKey, "planKey must not be null");
    this.provenanceCode = provenanceCode;
    this.operationCode = operationCode;
    this.exprProtoHash = exprProtoHash;
    this.exprProtoSnapshotJson = exprProtoSnapshotJson;
    this.provenanceConfigSnapshotJson = provenanceConfigSnapshotJson;
    this.provenanceConfigHash = provenanceConfigHash;
    this.windowSpec = Objects.requireNonNull(windowSpec, "windowSpec must not be null");
    this.sliceStrategyCode = sliceStrategyCode;
    this.sliceParamsJson = sliceParamsJson;
    this.status = status == null ? PlanStatus.DRAFT : status;
  }

  /**
   * Create a brand-new plan blueprint aggregate in {@link PlanStatus#DRAFT DRAFT} status.
   *
   * @param scheduleInstanceId scheduler instance identifier
   * @param planKey idempotency key
   * @param provenanceCode provenance/source code
   * @param operationCode operation code value (parsed into the enum)
   * @param exprProtoHash expression prototype hash
   * @param exprProtoSnapshotJson expression prototype snapshot JSON
   * @param provenanceConfigSnapshotJson provenance configuration snapshot JSON
   * @param provenanceConfigHash provenance configuration snapshot hash
   * @param windowSpec window boundary specification
   * @param sliceStrategyCode slicing strategy code
   * @param sliceParamsJson slicing strategy parameter JSON
   * @return a newly created plan aggregate
   */
  public static PlanAggregate create(
      Long scheduleInstanceId,
      String planKey,
      String provenanceCode,
      String operationCode,
      String exprProtoHash,
      String exprProtoSnapshotJson,
      String provenanceConfigSnapshotJson,
      String provenanceConfigHash,
      WindowSpec windowSpec,
      String sliceStrategyCode,
      String sliceParamsJson) {
    // Parse domain enum once to normalize case and whitespace.
    OperationCode op = operationCode == null ? null : OperationCode.fromCode(operationCode);
    return new PlanAggregate(
        null,
        scheduleInstanceId,
        planKey,
        provenanceCode,
        op,
        exprProtoHash,
        exprProtoSnapshotJson,
        provenanceConfigSnapshotJson,
        provenanceConfigHash,
        windowSpec,
        sliceStrategyCode,
        sliceParamsJson,
        PlanStatus.DRAFT);
  }

  /**
   * Rebuild an existing plan aggregate from persisted state (used by repositories).
   *
   * @param id primary identifier
   * @param scheduleInstanceId scheduler instance identifier
   * @param planKey plan idempotency key
   * @param provenanceCode provenance/source code
   * @param operationCode operation code string
   * @param exprProtoHash expression hash
   * @param exprProtoSnapshotJson expression snapshot JSON
   * @param provenanceConfigSnapshotJson configuration snapshot JSON
   * @param provenanceConfigHash configuration snapshot hash
   * @param windowSpec window boundary specification
   * @param sliceStrategyCode slicing strategy code
   * @param sliceParamsJson slicing strategy parameter JSON
   * @param status current plan status
   * @param version optimistic locking version
   * @return plan aggregate reconstructed from persistence
   */
  public static PlanAggregate restore(
      Long id,
      Long scheduleInstanceId,
      String planKey,
      String provenanceCode,
      String operationCode,
      String exprProtoHash,
      String exprProtoSnapshotJson,
      String provenanceConfigSnapshotJson,
      String provenanceConfigHash,
      WindowSpec windowSpec,
      String sliceStrategyCode,
      String sliceParamsJson,
      PlanStatus status,
      long version) {
    OperationCode op = operationCode == null ? null : OperationCode.fromCode(operationCode);
    PlanAggregate aggregate =
        new PlanAggregate(
            id,
            scheduleInstanceId,
            planKey,
            provenanceCode,
            op,
            exprProtoHash,
            exprProtoSnapshotJson,
            provenanceConfigSnapshotJson,
            provenanceConfigHash,
            windowSpec,
            sliceStrategyCode,
            sliceParamsJson,
            status);
    aggregate.assignVersion(version);
    return aggregate;
  }

  public void startSlicing() {
    if (this.status != PlanStatus.DRAFT) {
      throw new IllegalStateException("Invalid plan status; slicing cannot start.");
    }
    this.status = PlanStatus.SLICING;
  }

  /** Mark the plan as ready after all slices are generated. */
  public void markReady() {
    this.status = PlanStatus.READY;
  }

  /** Mark the plan as partially successful (some slices are still retrying). */
  public void markPartial() {
    this.status = PlanStatus.PARTIAL;
  }

  /** Mark the plan as failed, indicating termination or the need for compensation. */
  public void markFailed() {
    this.status = PlanStatus.FAILED;
  }

  /** Mark the plan as completed after all tasks finish successfully. */
  public void markCompleted() {
    this.status = PlanStatus.COMPLETED;
  }

  public Long getScheduleInstanceId() {
    return scheduleInstanceId;
  }

  /**
   * Obtain the idempotency key of this plan.
   *
   * @return planKey
   */
  public String getPlanKey() {
    return planKey;
  }

  public String getProvenanceCode() {
    return provenanceCode;
  }

  /**
   * Obtain the operation code string, if one is present.
   *
   * @return operation code or {@code null}
   */
  public String getOperationCode() {
    return operationCode == null ? null : operationCode.getCode();
  }

  // ========== Native enum accessor for internal domain use ==========
  public OperationCode getOperation() {
    return operationCode;
  }

  public String getExprProtoHash() {
    return exprProtoHash;
  }

  public String getExprProtoSnapshotJson() {
    return exprProtoSnapshotJson;
  }

  public String getProvenanceConfigSnapshotJson() {
    return provenanceConfigSnapshotJson;
  }

  public String getProvenanceConfigHash() {
    return provenanceConfigHash;
  }

  /**
   * Expose the raw window specification.
   *
   * @return window specification
   */
  public WindowSpec getWindowSpec() {
    return windowSpec;
  }

  /**
   * Convenience accessor that returns the window start when a TIME strategy is used. Returns {@code
   * null} for strategies that do not have a time window.
   *
   * @return window start time or {@code null}
   */
  public Instant getWindowFrom() {
    if (windowSpec instanceof WindowSpec.Time timeSpec) {
      return timeSpec.from();
    }
    return null;
  }

  /**
   * Convenience accessor that returns the window end when a TIME strategy is used. Returns {@code
   * null} for strategies that do not expose a time window.
   *
   * @return window end time or {@code null}
   */
  public Instant getWindowTo() {
    if (windowSpec instanceof WindowSpec.Time timeSpec) {
      return timeSpec.to();
    }
    return null;
  }

  public String getSliceStrategyCode() {
    return sliceStrategyCode;
  }

  public String getSliceParamsJson() {
    return sliceParamsJson;
  }

  /** Return the current {@link PlanStatus} for this plan. */
  public PlanStatus getStatus() {
    return status;
  }
}
