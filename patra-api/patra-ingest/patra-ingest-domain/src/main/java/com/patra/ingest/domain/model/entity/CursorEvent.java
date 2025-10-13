package com.patra.ingest.domain.model.entity;

import com.patra.ingest.domain.model.enums.CursorDirection;
import com.patra.ingest.domain.model.enums.CursorType;
import com.patra.ingest.domain.model.vo.CursorLineage;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;

/**
 * Append-only cursor advancement event.
 *
 * <p>Captures forward or backward movements for a cursor within a namespace to support:
 *
 * <ul>
 *   <li>Audit: trace the source and value changes for each window advancement.
 *   <li>Rebuild: replay events when restoring cursor state.
 *   <li>Monitoring: analyze advancement speed, rollback ratio, and lag.
 * </ul>
 *
 * <p>Design principles: immutable; historical records are never mutated; fields cover
 * string/time/numeric representations to avoid precision loss.
 */
@SuppressWarnings("unused")
@Getter
public class CursorEvent {
  /** Primary key ID. */
  private final Long id;

  /** Provenance code. */
  private final String provenanceCode;

  /** Operation code. */
  private final String operationCode;

  /** Logical cursor key (distinguishes dimensions within a provenance). */
  private final String cursorKey;

  /** Namespace scope code (e.g., tenant or data domain). */
  private final String namespaceScopeCode;

  /** Namespace business key. */
  private final String namespaceKey;

  /** Cursor type (time, numeric, lexicographic, etc.). */
  private final CursorType cursorType;

  /** Previous value (raw string). */
  private final String prevValue;

  /** New value (raw string). */
  private final String newValue;

  /** Advancement direction (forward or rollback). */
  private final CursorDirection direction;

  /** Idempotency key used to guard against duplicates. */
  private final String idempotentKey;

  /** Observed maximum value during advancement (for lag assessment). */
  private final String observedMaxValue;

  /** Parsed instant for the previous value (if applicable). */
  private final Instant prevInstant;

  /** Parsed instant for the new value (if applicable). */
  private final Instant newInstant;

  /** Parsed numeric value for the previous cursor (if applicable). */
  private final BigDecimal prevNumeric;

  /** Parsed numeric value for the new cursor (if applicable). */
  private final BigDecimal newNumeric;

  /** Lineage information describing expression dependencies. */
  private final CursorLineage lineage;

  /** Hash of the expression used during advancement (tracks strategy changes). */
  private final String exprHash;

  private CursorEvent(
      Long id,
      String provenanceCode,
      String operationCode,
      String cursorKey,
      String namespaceScopeCode,
      String namespaceKey,
      CursorType cursorType,
      String prevValue,
      String newValue,
      CursorDirection direction,
      String idempotentKey,
      String observedMaxValue,
      Instant prevInstant,
      Instant newInstant,
      BigDecimal prevNumeric,
      BigDecimal newNumeric,
      CursorLineage lineage,
      String exprHash) {
    this.id = id;
    this.provenanceCode = provenanceCode;
    this.operationCode = operationCode;
    this.cursorKey = cursorKey;
    this.namespaceScopeCode = namespaceScopeCode;
    this.namespaceKey = namespaceKey;
    this.cursorType = cursorType;
    this.prevValue = prevValue;
    this.newValue = newValue;
    this.direction = direction;
    this.idempotentKey = idempotentKey;
    this.observedMaxValue = observedMaxValue;
    this.prevInstant = prevInstant;
    this.newInstant = newInstant;
    this.prevNumeric = prevNumeric;
    this.newNumeric = newNumeric;
    this.lineage = lineage == null ? CursorLineage.empty() : lineage;
    this.exprHash = exprHash;
  }

  /**
   * Restore (deserialize) a cursor event from persisted fields.
   *
   * @param id primary key
   * @param provenanceCode provenance code
   * @param operationCode operation code
   * @param cursorKey cursor key
   * @param namespaceScopeCode namespace scope code
   * @param namespaceKey namespace key
   * @param cursorType cursor type
   * @param prevValue previous value (string)
   * @param newValue new value (string)
   * @param direction advancement direction
   * @param idempotentKey idempotency key
   * @param observedMaxValue observed maximum value
   * @param prevInstant previous instant
   * @param newInstant new instant
   * @param prevNumeric previous numeric value
   * @param newNumeric new numeric value
   * @param lineage lineage metadata
   * @param exprHash expression hash
   * @return {@link CursorEvent} instance
   */
  public static CursorEvent restore(
      Long id,
      String provenanceCode,
      String operationCode,
      String cursorKey,
      String namespaceScopeCode,
      String namespaceKey,
      CursorType cursorType,
      String prevValue,
      String newValue,
      CursorDirection direction,
      String idempotentKey,
      String observedMaxValue,
      Instant prevInstant,
      Instant newInstant,
      BigDecimal prevNumeric,
      BigDecimal newNumeric,
      CursorLineage lineage,
      String exprHash) {
    return new CursorEvent(
        id,
        provenanceCode,
        operationCode,
        cursorKey,
        namespaceScopeCode,
        namespaceKey,
        cursorType,
        prevValue,
        newValue,
        direction,
        idempotentKey,
        observedMaxValue,
        prevInstant,
        newInstant,
        prevNumeric,
        newNumeric,
        lineage,
        exprHash);
  }
}
