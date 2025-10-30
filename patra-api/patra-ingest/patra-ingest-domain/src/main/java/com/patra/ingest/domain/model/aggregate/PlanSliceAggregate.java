package com.patra.ingest.domain.model.aggregate;

import com.patra.common.domain.AggregateRoot;
import com.patra.ingest.domain.model.enums.SliceStatus;
import java.util.Objects;
import lombok.Getter;

/**
 * Aggregate root that models the signature and lifecycle of an ingestion plan slice.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Getter
public class PlanSliceAggregate extends AggregateRoot<Long> {

  /** Identifier of the plan this slice belongs to. */
  private Long planId;

  /** Provenance/source code. */
  private final String provenanceCode;

  /** Slice sequence number. */
  private final int sliceNo;

  /** Slice signature hash. */
  private final String sliceSignatureHash;

  /** Window specification serialized as JSON. */
  private final String windowSpecJson;

  /** Hash of the slice-scoped expression. */
  private final String exprHash;

  /** Slice-scoped expression snapshot JSON. */
  private final String exprSnapshotJson;

  /** Current status of the slice. */
  private SliceStatus status;

  private PlanSliceAggregate(
      Long id,
      Long planId,
      String provenanceCode,
      int sliceNo,
      String sliceSignatureHash,
      String windowSpecJson,
      String exprHash,
      String exprSnapshotJson,
      SliceStatus status) {
    super(id);
    this.planId = planId;
    this.provenanceCode = provenanceCode;
    this.sliceNo = sliceNo;
    this.sliceSignatureHash = sliceSignatureHash;
    this.windowSpecJson = windowSpecJson;
    this.exprHash = exprHash;
    this.exprSnapshotJson = exprSnapshotJson;
    this.status = status == null ? SliceStatus.PENDING : status;
  }

  public static PlanSliceAggregate create(
      Long planId,
      String provenanceCode,
      int sliceNo,
      String sliceSignatureHash,
      String windowSpecJson,
      String exprHash,
      String exprSnapshotJson) {
    Objects.requireNonNull(sliceSignatureHash, "sliceSignatureHash must not be null");
    return new PlanSliceAggregate(
        null,
        planId,
        provenanceCode,
        sliceNo,
        sliceSignatureHash,
        windowSpecJson,
        exprHash,
        exprSnapshotJson,
        SliceStatus.PENDING);
  }

  public static PlanSliceAggregate restore(
      Long id,
      Long planId,
      String provenanceCode,
      int sequence,
      String sliceSignatureHash,
      String windowSpecJson,
      String exprHash,
      String exprSnapshotJson,
      SliceStatus status,
      long version) {
    PlanSliceAggregate aggregate =
        new PlanSliceAggregate(
            id,
            planId,
            provenanceCode,
            sequence,
            sliceSignatureHash,
            windowSpecJson,
            exprHash,
            exprSnapshotJson,
            status);
    aggregate.assignVersion(version);
    return aggregate;
  }

  /**
   * Binds this slice to a specific plan after persistence.
   *
   * @param planId plan identifier
   * @throws IllegalArgumentException if planId is null
   */
  public void bindPlan(Long planId) {
    if (planId == null) {
      throw new IllegalArgumentException("planId must not be null");
    }
    this.planId = planId;
  }

  /**
   * Marks the slice as assigned (corresponding Task has been created).
   *
   * <p><b>Note:</b> After refactoring, enforces 1:1 Slice-Task relationship.
   */
  public void markAssigned() {
    this.status = SliceStatus.ASSIGNED;
  }

  /**
   * Updates the slice status to the specified value.
   *
   * <p>This method is used by event handlers to update the status based on aggregated task states.
   *
   * @param newStatus the new status to set
   * @throws IllegalArgumentException if newStatus is null
   */
  public void updateStatus(SliceStatus newStatus) {
    if (newStatus == null) {
      throw new IllegalArgumentException("newStatus must not be null");
    }
    this.status = newStatus;
  }
}
