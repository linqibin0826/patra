package com.patra.ingest.domain.model.entity;

import com.patra.ingest.domain.model.enums.CursorType;
import com.patra.ingest.domain.model.enums.NamespaceScope;
import com.patra.ingest.domain.model.vo.cursor.CursorLineage;
import com.patra.ingest.domain.model.vo.cursor.CursorValue;
import com.patra.ingest.domain.model.vo.cursor.CursorWatermark;
import java.util.Objects;
import lombok.Getter;

/**
 * 游标实体。表示增量采集的当前游标状态。
 *
 * <p>标识：由 provenanceCode + operationCode + cursorKey + namespaceScope + namespaceKey 唯一标识。
 *
 * <p>生命周期：
 *
 * <ul>
 *   <li>首次采集时创建游标，记录初始水位线
 *   <li>每次采集完成后前进游标，更新水位线和血缘信息
 *   <li>表达式哈希变更时需要重置游标
 * </ul>
 *
 * <p>业务约束：
 *
 * <ul>
 *   <li>游标水位线单调递增，不允许回退
 *   <li>支持 GLOBAL/TASK/PLAN 三种命名空间范围
 *   <li>游标值 (CursorValue) 支持时间、数值、字符串等多种类型
 *   <li>水位线 (CursorWatermark) 记录归一化的时间戳或数值
 *   <li>血缘 (CursorLineage) 捕获 task/run/plan/slice 标识用于追溯
 * </ul>
 */
@SuppressWarnings("unused")
@Getter
public class Cursor {

  /** 游标前进操作的结果。 */
  public enum AdvancementResult {
    /** 游标成功前进。 */
    SUCCESS,
    /** 表达式哈希已变更，游标需要重置。 */
    EXPRESSION_CHANGED
  }

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
    return create(
        provenanceCode,
        operationCode,
        cursorKey,
        namespaceScope,
        namespaceKey,
        watermark,
        lineage,
        null);
  }

  /**
   * Factory method creating a time-based cursor with lineage context and expression hash.
   *
   * @param provenanceCode provenance code
   * @param operationCode operation code
   * @param cursorKey cursor key identifier
   * @param namespaceScope namespace scope (GLOBAL/TASK/PLAN)
   * @param namespaceKey namespace key
   * @param watermark initial watermark timestamp
   * @param lineage lineage context capturing task/run/plan/slice identifiers
   * @param exprHash expression hash for tracking strategy changes
   * @return new cursor instance
   */
  public static Cursor create(
      String provenanceCode,
      String operationCode,
      String cursorKey,
      String namespaceScope,
      String namespaceKey,
      java.time.Instant watermark,
      CursorLineage lineage,
      String exprHash) {
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
        exprHash);
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

  /**
   * Advance the cursor to the supplied time watermark with expression hash tracking.
   *
   * <p>This method detects expression changes and returns a result indicating whether the cursor
   * was advanced successfully or if the expression hash has changed (requiring a reset).
   *
   * @param newWatermark new watermark timestamp
   * @param newLineage new lineage context (optional, keeps existing if null)
   * @param newExprHash new expression hash (nullable)
   * @return {@link AdvancementResult#SUCCESS} if advanced, {@link
   *     AdvancementResult#EXPRESSION_CHANGED} if expression hash changed
   */
  public AdvancementResult advanceTo(
      java.time.Instant newWatermark, CursorLineage newLineage, String newExprHash) {
    if (newWatermark == null) {
      throw new IllegalArgumentException("New watermark must not be null");
    }

    // Check for expression hash change (null-safe comparison)
    if (!matchesExpression(newExprHash)) {
      return AdvancementResult.EXPRESSION_CHANGED;
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

    // Update cursor state
    this.watermark = new CursorWatermark(newWatermark.toString(), newWatermark, null);
    if (newLineage != null) {
      this.lineage = newLineage;
    }
    this.exprHash = newExprHash;

    return AdvancementResult.SUCCESS;
  }

  /**
   * Check if the cursor's expression hash matches the given expression hash.
   *
   * <p>Uses null-safe comparison to handle cases where either hash might be null.
   *
   * @param exprHash expression hash to compare against
   * @return true if hashes match, false otherwise
   */
  public boolean matchesExpression(String exprHash) {
    return Objects.equals(this.exprHash, exprHash);
  }

  /** Return the current time-based watermark. */
  public java.time.Instant getCurrentWatermark() {
    return watermark.normalizedInstant();
  }
}
