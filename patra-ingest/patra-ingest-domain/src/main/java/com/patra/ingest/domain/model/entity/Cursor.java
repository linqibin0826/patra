package com.patra.ingest.domain.model.entity;

import com.patra.ingest.domain.model.enums.CursorType;
import com.patra.ingest.domain.model.enums.NamespaceScope;
import com.patra.ingest.domain.model.vo.CursorLineage;
import com.patra.ingest.domain.model.vo.CursorValue;
import com.patra.ingest.domain.model.vo.CursorWatermark;
import lombok.Getter;

/** Domain entity representing the current cursor state. */
@SuppressWarnings("unused")
@Getter
public class Cursor {
  private final Long id;
  private final String provenanceCode;
  private final String operationCode;
  private final String cursorKey;
  private final NamespaceScope namespaceScope;
  private final String namespaceKey;
  private final CursorType cursorType;
  private CursorValue value;
  private CursorWatermark watermark;
  private CursorLineage lineage;
  private String exprHash;

  private Cursor(
      Long id,
      String provenanceCode,
      String operationCode,
      String cursorKey,
      NamespaceScope namespaceScope,
      String namespaceKey,
      CursorType cursorType,
      CursorValue value,
      CursorWatermark watermark,
      CursorLineage lineage,
      String exprHash) {
    this.id = id;
    this.provenanceCode = provenanceCode;
    this.operationCode = operationCode;
    this.cursorKey = cursorKey;
    this.namespaceScope = namespaceScope;
    this.namespaceKey = namespaceKey;
    this.cursorType = cursorType;
    this.value = value;
    this.watermark = watermark == null ? CursorWatermark.empty() : watermark;
    this.lineage = lineage == null ? CursorLineage.empty() : lineage;
    this.exprHash = exprHash;
  }

  public static Cursor restore(
      Long id,
      String provenanceCode,
      String operationCode,
      String cursorKey,
      NamespaceScope namespaceScope,
      String namespaceKey,
      CursorType cursorType,
      CursorValue value,
      CursorWatermark watermark,
      CursorLineage lineage,
      String exprHash) {
    return new Cursor(
        id,
        provenanceCode,
        operationCode,
        cursorKey,
        namespaceScope,
        namespaceKey,
        cursorType,
        value,
        watermark,
        lineage,
        exprHash);
  }

  public void advance(
      CursorValue newValue,
      CursorWatermark newWatermark,
      CursorLineage newLineage,
      String newExprHash) {
    if (newValue == null) {
      throw new IllegalArgumentException("Cursor value must not be null");
    }
    this.value = newValue;
    if (newWatermark != null) {
      this.watermark = newWatermark;
    }
    if (newLineage != null) {
      this.lineage = newLineage;
    }
    if (newExprHash != null && !newExprHash.isBlank()) {
      this.exprHash = newExprHash;
    }
  }

  public void updateObservedMax(String observedMax) {
    this.watermark =
        new CursorWatermark(
            observedMax, watermark.normalizedInstant(), watermark.normalizedNumeric());
  }

  /** Factory method creating a time-based cursor. */
  public static Cursor create(
      String provenanceCode,
      String operationCode,
      String cursorKey,
      String namespaceScope,
      String namespaceKey,
      java.time.Instant watermark) {
    return create(
        provenanceCode,
        operationCode,
        cursorKey,
        namespaceScope,
        namespaceKey,
        watermark,
        CursorLineage.empty());
  }

  /**
   * Factory method creating a time-based cursor with lineage context.
   *
   * @param provenanceCode provenance code
   * @param operationCode operation code
   * @param cursorKey cursor key identifier
   * @param namespaceScope namespace scope (GLOBAL/TASK/PLAN)
   * @param namespaceKey namespace key
   * @param watermark initial watermark timestamp
   * @param lineage lineage context capturing task/run/plan/slice identifiers
   * @return new cursor instance
   */
  public static Cursor create(
      String provenanceCode,
      String operationCode,
      String cursorKey,
      String namespaceScope,
      String namespaceKey,
      java.time.Instant watermark,
      CursorLineage lineage) {
    NamespaceScope scope = NamespaceScope.fromCode(namespaceScope);
    CursorType type = CursorType.TIME;
    CursorValue value = watermark == null ? CursorValue.empty() : CursorValue.time(watermark);
    CursorWatermark watermarkVO =
        new CursorWatermark(watermark == null ? null : watermark.toString(), watermark, null);
    return new Cursor(
        null,
        provenanceCode,
        operationCode,
        cursorKey,
        scope,
        namespaceKey,
        type,
        value,
        watermarkVO,
        lineage == null ? CursorLineage.empty() : lineage,
        null);
  }

  /** Advance the cursor to the supplied time watermark. */
  public void advanceTo(java.time.Instant newWatermark) {
    advanceTo(newWatermark, null);
  }

  /**
   * Advance the cursor to the supplied time watermark with lineage update.
   *
   * @param newWatermark new watermark timestamp
   * @param newLineage new lineage context (optional, keeps existing if null)
   */
  public void advanceTo(java.time.Instant newWatermark, CursorLineage newLineage) {
    if (newWatermark == null) {
      throw new IllegalArgumentException("New watermark must not be null");
    }
    // Ensure the watermark never moves backwards
    if (watermark.normalizedInstant() != null
        && newWatermark.isBefore(watermark.normalizedInstant())) {
      throw new IllegalArgumentException(
          "Cursor watermark cannot move backwards current="
              + watermark.normalizedInstant()
              + " new="
              + newWatermark);
    }
    this.watermark = new CursorWatermark(newWatermark.toString(), newWatermark, null);
    if (newLineage != null) {
      this.lineage = newLineage;
    }
  }

  /** Return the current time-based watermark. */
  public java.time.Instant getCurrentWatermark() {
    return watermark.normalizedInstant();
  }
}
