package com.patra.ingest.domain.model.entity;

import static org.assertj.core.api.Assertions.*;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.CursorType;
import com.patra.ingest.domain.model.enums.NamespaceScope;
import com.patra.ingest.domain.model.vo.cursor.CursorLineage;
import com.patra.ingest.domain.model.vo.cursor.CursorValue;
import com.patra.ingest.domain.model.vo.cursor.CursorWatermark;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// Cursor 单元测试。
///
/// 测试策略：
///
/// - 纯 Java 单元测试，不依赖 Spring 容器
///   - 使用 TestDataBuilder 模式构建测试数据
///   - 遵循 Given-When-Then 结构
///   - 使用 AssertJ 流畅断言
///
/// 测试范围：
///
/// - ✅ 工厂方法测试（create, restore）
///   - ✅ 游标前进测试（advance, advanceTo）
///   - ✅ 水位线单调性测试（不允许回退）
///   - ✅ 表达式哈希检测测试（matchesExpression, AdvancementResult）
///   - ✅ 命名空间范围测试（GLOBAL/EXPR/CUSTOM）
///   - ✅ 边界条件和异常情况
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("Cursor 单元测试")
class CursorTest {

  // ========== 工厂方法测试 ==========

  @Nested
  @DisplayName("create() 工厂方法")
  class CreateFactoryMethodTests {

    @Test
    @DisplayName("应该成功创建基于时间的游标")
    void shouldCreateTimeBasedCursor() {
      // Given
      ProvenanceCode provenanceCode = ProvenanceCode.PUBMED;
      String operationCode = "HARVEST";
      String cursorKey = "publication_date";
      String namespaceScope = "GLOBAL";
      String namespaceKey = "global";
      Instant watermark = Instant.parse("2024-01-01T00:00:00Z");

      // When
      Cursor cursor =
          Cursor.create(
              provenanceCode, operationCode, cursorKey, namespaceScope, namespaceKey, watermark);

      // Then
      assertThat(cursor).isNotNull();
      assertThat(cursor.getId()).isNull(); // 新创建的实体 ID 为 null
      assertThat(cursor.getProvenanceCode()).isEqualTo(provenanceCode);
      assertThat(cursor.getOperationCode()).isEqualTo(operationCode);
      assertThat(cursor.getCursorKey()).isEqualTo(cursorKey);
      assertThat(cursor.getNamespaceScope()).isEqualTo(NamespaceScope.GLOBAL);
      assertThat(cursor.getNamespaceKey()).isEqualTo(namespaceKey);
      assertThat(cursor.getCursorType()).isEqualTo(CursorType.TIME);
      assertThat(cursor.getValue()).isNotNull();
      assertThat(cursor.getValue().type()).isEqualTo(CursorType.TIME);
      assertThat(cursor.getValue().instant()).isEqualTo(watermark);
      assertThat(cursor.getWatermark().normalizedInstant()).isEqualTo(watermark);
      assertThat(cursor.getWatermark().observedMaxValue()).isEqualTo(watermark.toString());
      assertThat(cursor.getLineage()).isEqualTo(CursorLineage.empty());
      assertThat(cursor.getExprHash()).isNull();
    }

    @Test
    @DisplayName("应该成功创建带血缘上下文的时间游标")
    void shouldCreateTimeBasedCursorWithLineage() {
      // Given
      Instant watermark = Instant.parse("2024-01-01T00:00:00Z");
      CursorLineage lineage =
          new CursorLineage(
              1001L, // scheduleInstanceId
              2001L, // planId
              3001L, // sliceId
              4001L, // taskId
              5001L, // runId
              6001L // batchId
              );

      // When
      Cursor cursor =
          Cursor.create(
              ProvenanceCode.PUBMED,
              "HARVEST",
              "publication_date",
              "GLOBAL",
              "global",
              watermark,
              lineage);

      // Then
      assertThat(cursor.getLineage()).isEqualTo(lineage);
      assertThat(cursor.getLineage().scheduleInstanceId()).isEqualTo(1001L);
      assertThat(cursor.getLineage().planId()).isEqualTo(2001L);
      assertThat(cursor.getLineage().sliceId()).isEqualTo(3001L);
      assertThat(cursor.getLineage().taskId()).isEqualTo(4001L);
      assertThat(cursor.getLineage().runId()).isEqualTo(5001L);
      assertThat(cursor.getLineage().batchId()).isEqualTo(6001L);
    }

    @Test
    @DisplayName("应该成功创建带表达式哈希的时间游标")
    void shouldCreateTimeBasedCursorWithExpressionHash() {
      // Given
      Instant watermark = Instant.parse("2024-01-01T00:00:00Z");
      String exprHash = "expr-hash-12345";

      // When
      Cursor cursor =
          Cursor.create(
              ProvenanceCode.PUBMED,
              "HARVEST",
              "publication_date",
              "GLOBAL",
              "global",
              watermark,
              CursorLineage.empty(),
              exprHash);

      // Then
      assertThat(cursor.getExprHash()).isEqualTo(exprHash);
    }

    @Test
    @DisplayName("应该正确处理 null 水位线")
    void shouldHandleNullWatermark() {
      // Given
      Instant watermark = null;

      // When
      Cursor cursor =
          Cursor.create(
              ProvenanceCode.PUBMED, "HARVEST", "publication_date", "GLOBAL", "global", watermark);

      // Then
      assertThat(cursor.getValue()).isEqualTo(CursorValue.empty());
      assertThat(cursor.getWatermark().normalizedInstant()).isNull();
      assertThat(cursor.getWatermark().observedMaxValue()).isNull();
    }

    @Test
    @DisplayName("应该正确处理 null 血缘上下文")
    void shouldHandleNullLineage() {
      // Given
      CursorLineage lineage = null;

      // When
      Cursor cursor =
          Cursor.create(
              ProvenanceCode.PUBMED,
              "HARVEST",
              "publication_date",
              "GLOBAL",
              "global",
              Instant.parse("2024-01-01T00:00:00Z"),
              lineage);

      // Then
      assertThat(cursor.getLineage()).isEqualTo(CursorLineage.empty());
    }

    @Test
    @DisplayName("应该正确解析命名空间范围")
    void shouldParseNamespaceScope() {
      // Given & When - 测试所有命名空间范围
      Cursor globalCursor =
          Cursor.create(
              ProvenanceCode.PUBMED,
              "HARVEST",
              "publication_date",
              "GLOBAL",
              "global",
              Instant.parse("2024-01-01T00:00:00Z"));

      Cursor exprCursor =
          Cursor.create(
              ProvenanceCode.PUBMED,
              "HARVEST",
              "publication_date",
              "EXPR",
              "expr-hash-12345",
              Instant.parse("2024-01-01T00:00:00Z"));

      Cursor customCursor =
          Cursor.create(
              ProvenanceCode.PUBMED,
              "HARVEST",
              "publication_date",
              "CUSTOM",
              "custom-key",
              Instant.parse("2024-01-01T00:00:00Z"));

      // Then
      assertThat(globalCursor.getNamespaceScope()).isEqualTo(NamespaceScope.GLOBAL);
      assertThat(exprCursor.getNamespaceScope()).isEqualTo(NamespaceScope.EXPR);
      assertThat(customCursor.getNamespaceScope()).isEqualTo(NamespaceScope.CUSTOM);
    }

    @Test
    @DisplayName("应该抛出异常当命名空间范围无效")
    void shouldThrowExceptionWhenNamespaceScopeIsInvalid() {
      // Given
      String invalidScope = "INVALID_SCOPE";

      // When & Then
      assertThatThrownBy(
              () ->
                  Cursor.create(
                      ProvenanceCode.PUBMED,
                      "HARVEST",
                      "publication_date",
                      invalidScope,
                      "key",
                      Instant.parse("2024-01-01T00:00:00Z")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("未知的命名空间范围代码");
    }
  }

  @Nested
  @DisplayName("restore() 工厂方法")
  class RestoreFactoryMethodTests {

    @Test
    @DisplayName("应该从持久化状态成功重建游标")
    void shouldRestoreCursorFromPersistentState() {
      // Given
      Long id = 100L;
      ProvenanceCode provenanceCode = ProvenanceCode.PUBMED;
      String operationCode = "HARVEST";
      String cursorKey = "publication_date";
      NamespaceScope namespaceScope = NamespaceScope.GLOBAL;
      String namespaceKey = "global";
      CursorType cursorType = CursorType.TIME;
      Instant watermark = Instant.parse("2024-01-01T00:00:00Z");
      CursorValue value = CursorValue.time(watermark);
      CursorWatermark watermarkVO = new CursorWatermark(watermark.toString(), watermark, null);
      CursorLineage lineage = new CursorLineage(1001L, 2001L, 3001L, 4001L, 5001L, 6001L);
      String exprHash = "expr-hash-12345";

      // When
      Cursor cursor =
          Cursor.restore(
              id,
              provenanceCode,
              operationCode,
              cursorKey,
              namespaceScope,
              namespaceKey,
              cursorType,
              value,
              watermarkVO,
              lineage,
              exprHash);

      // Then
      assertThat(cursor).isNotNull();
      assertThat(cursor.getId()).isEqualTo(id);
      assertThat(cursor.getProvenanceCode()).isEqualTo(provenanceCode);
      assertThat(cursor.getOperationCode()).isEqualTo(operationCode);
      assertThat(cursor.getCursorKey()).isEqualTo(cursorKey);
      assertThat(cursor.getNamespaceScope()).isEqualTo(namespaceScope);
      assertThat(cursor.getNamespaceKey()).isEqualTo(namespaceKey);
      assertThat(cursor.getCursorType()).isEqualTo(cursorType);
      assertThat(cursor.getValue()).isEqualTo(value);
      assertThat(cursor.getWatermark()).isEqualTo(watermarkVO);
      assertThat(cursor.getLineage()).isEqualTo(lineage);
      assertThat(cursor.getExprHash()).isEqualTo(exprHash);
    }

    @Test
    @DisplayName("应该使用默认空值当水位线为 null")
    void shouldUseEmptyWatermarkWhenNull() {
      // Given
      CursorWatermark watermark = null;

      // When
      Cursor cursor =
          Cursor.restore(
              100L,
              ProvenanceCode.PUBMED,
              "HARVEST",
              "publication_date",
              NamespaceScope.GLOBAL,
              "global",
              CursorType.TIME,
              CursorValue.empty(),
              watermark,
              CursorLineage.empty(),
              null);

      // Then
      assertThat(cursor.getWatermark()).isEqualTo(CursorWatermark.empty());
    }

    @Test
    @DisplayName("应该使用默认空值当血缘为 null")
    void shouldUseEmptyLineageWhenNull() {
      // Given
      CursorLineage lineage = null;

      // When
      Cursor cursor =
          Cursor.restore(
              100L,
              ProvenanceCode.PUBMED,
              "HARVEST",
              "publication_date",
              NamespaceScope.GLOBAL,
              "global",
              CursorType.TIME,
              CursorValue.empty(),
              CursorWatermark.empty(),
              lineage,
              null);

      // Then
      assertThat(cursor.getLineage()).isEqualTo(CursorLineage.empty());
    }
  }

  // ========== 游标前进测试 ==========

  @Nested
  @DisplayName("advance() 方法")
  class AdvanceMethodTests {

    @Test
    @DisplayName("应该成功前进游标值")
    void shouldAdvanceCursorValue() {
      // Given
      Cursor cursor = CursorTestDataBuilder.aTimeCursor().build();
      Instant newWatermark = Instant.parse("2024-02-01T00:00:00Z");
      CursorValue newValue = CursorValue.time(newWatermark);

      // When
      cursor.advance(newValue, null, null, null);

      // Then
      assertThat(cursor.getValue()).isEqualTo(newValue);
      assertThat(cursor.getValue().instant()).isEqualTo(newWatermark);
    }

    @Test
    @DisplayName("应该更新水位线当提供新水位线")
    void shouldUpdateWatermarkWhenProvided() {
      // Given
      Cursor cursor = CursorTestDataBuilder.aTimeCursor().build();
      Instant newWatermark = Instant.parse("2024-02-01T00:00:00Z");
      CursorValue newValue = CursorValue.time(newWatermark);
      CursorWatermark newWatermarkVO =
          new CursorWatermark(newWatermark.toString(), newWatermark, null);

      // When
      cursor.advance(newValue, newWatermarkVO, null, null);

      // Then
      assertThat(cursor.getWatermark()).isEqualTo(newWatermarkVO);
    }

    @Test
    @DisplayName("应该保持原水位线当新水位线为 null")
    void shouldKeepOriginalWatermarkWhenNewIsNull() {
      // Given
      Instant originalWatermark = Instant.parse("2024-01-01T00:00:00Z");
      Cursor cursor = CursorTestDataBuilder.aTimeCursor().watermark(originalWatermark).build();
      CursorWatermark originalWatermarkVO = cursor.getWatermark();

      Instant newWatermark = Instant.parse("2024-02-01T00:00:00Z");
      CursorValue newValue = CursorValue.time(newWatermark);

      // When - 不提供新水位线
      cursor.advance(newValue, null, null, null);

      // Then - 水位线应该保持不变
      assertThat(cursor.getWatermark()).isEqualTo(originalWatermarkVO);
    }

    @Test
    @DisplayName("应该更新血缘当提供新血缘")
    void shouldUpdateLineageWhenProvided() {
      // Given
      Cursor cursor = CursorTestDataBuilder.aTimeCursor().build();
      CursorLineage newLineage = new CursorLineage(9001L, 9002L, 9003L, 9004L, 9005L, 9006L);
      CursorValue newValue = CursorValue.time(Instant.parse("2024-02-01T00:00:00Z"));

      // When
      cursor.advance(newValue, null, newLineage, null);

      // Then
      assertThat(cursor.getLineage()).isEqualTo(newLineage);
    }

    @Test
    @DisplayName("应该保持原血缘当新血缘为 null")
    void shouldKeepOriginalLineageWhenNewIsNull() {
      // Given
      CursorLineage originalLineage = new CursorLineage(1001L, 2001L, 3001L, 4001L, 5001L, 6001L);
      Cursor cursor = CursorTestDataBuilder.aTimeCursor().lineage(originalLineage).build();
      CursorValue newValue = CursorValue.time(Instant.parse("2024-02-01T00:00:00Z"));

      // When - 不提供新血缘
      cursor.advance(newValue, null, null, null);

      // Then - 血缘应该保持不变
      assertThat(cursor.getLineage()).isEqualTo(originalLineage);
    }

    @Test
    @DisplayName("应该更新表达式哈希当提供新哈希")
    void shouldUpdateExprHashWhenProvided() {
      // Given
      Cursor cursor = CursorTestDataBuilder.aTimeCursor().exprHash("old-hash").build();
      String newExprHash = "new-hash-67890";
      CursorValue newValue = CursorValue.time(Instant.parse("2024-02-01T00:00:00Z"));

      // When
      cursor.advance(newValue, null, null, newExprHash);

      // Then
      assertThat(cursor.getExprHash()).isEqualTo(newExprHash);
    }

    @Test
    @DisplayName("应该保持原哈希当新哈希为 null")
    void shouldKeepOriginalHashWhenNewIsNull() {
      // Given
      String originalHash = "original-hash-12345";
      Cursor cursor = CursorTestDataBuilder.aTimeCursor().exprHash(originalHash).build();
      CursorValue newValue = CursorValue.time(Instant.parse("2024-02-01T00:00:00Z"));

      // When - 不提供新哈希
      cursor.advance(newValue, null, null, null);

      // Then - 哈希应该保持不变
      assertThat(cursor.getExprHash()).isEqualTo(originalHash);
    }

    @Test
    @DisplayName("应该保持原哈希当新哈希为空字符串")
    void shouldKeepOriginalHashWhenNewIsBlank() {
      // Given
      String originalHash = "original-hash-12345";
      Cursor cursor = CursorTestDataBuilder.aTimeCursor().exprHash(originalHash).build();
      CursorValue newValue = CursorValue.time(Instant.parse("2024-02-01T00:00:00Z"));

      // When - 提供空哈希
      cursor.advance(newValue, null, null, "  ");

      // Then - 哈希应该保持不变
      assertThat(cursor.getExprHash()).isEqualTo(originalHash);
    }

    @Test
    @DisplayName("应该抛出异常当新游标值为 null")
    void shouldThrowExceptionWhenNewValueIsNull() {
      // Given
      Cursor cursor = CursorTestDataBuilder.aTimeCursor().build();

      // When & Then
      assertThatThrownBy(() -> cursor.advance(null, null, null, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Cursor value must not be null");
    }
  }

  @Nested
  @DisplayName("advanceTo() 方法 - 时间游标")
  class AdvanceToTimeMethodTests {

    @Test
    @DisplayName("应该成功前进到新的时间水位线")
    void shouldAdvanceToNewTimeWatermark() {
      // Given
      Instant originalWatermark = Instant.parse("2024-01-01T00:00:00Z");
      Cursor cursor = CursorTestDataBuilder.aTimeCursor().watermark(originalWatermark).build();

      // When
      Instant newWatermark = Instant.parse("2024-02-01T00:00:00Z");
      cursor.advanceTo(newWatermark);

      // Then
      assertThat(cursor.getWatermark().normalizedInstant()).isEqualTo(newWatermark);
      assertThat(cursor.getWatermark().observedMaxValue()).isEqualTo(newWatermark.toString());
    }

    @Test
    @DisplayName("应该允许前进到相同的时间水位线")
    void shouldAllowAdvancingToSameTimeWatermark() {
      // Given
      Instant watermark = Instant.parse("2024-01-01T00:00:00Z");
      Cursor cursor = CursorTestDataBuilder.aTimeCursor().watermark(watermark).build();

      // When & Then - 前进到相同水位线应该成功
      assertThatNoException().isThrownBy(() -> cursor.advanceTo(watermark));
      assertThat(cursor.getWatermark().normalizedInstant()).isEqualTo(watermark);
    }

    @Test
    @DisplayName("应该抛出异常当尝试将水位线回退")
    void shouldThrowExceptionWhenMovingWatermarkBackwards() {
      // Given
      Instant currentWatermark = Instant.parse("2024-02-01T00:00:00Z");
      Cursor cursor = CursorTestDataBuilder.aTimeCursor().watermark(currentWatermark).build();

      // When & Then - 尝试回退水位线应该失败
      Instant olderWatermark = Instant.parse("2024-01-01T00:00:00Z");
      assertThatThrownBy(() -> cursor.advanceTo(olderWatermark))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Cursor watermark cannot move backwards")
          .hasMessageContaining("current=" + currentWatermark)
          .hasMessageContaining("new=" + olderWatermark);
    }

    @Test
    @DisplayName("应该抛出异常当新水位线为 null")
    void shouldThrowExceptionWhenNewWatermarkIsNull() {
      // Given
      Cursor cursor = CursorTestDataBuilder.aTimeCursor().build();

      // When & Then
      assertThatThrownBy(() -> cursor.advanceTo((Instant) null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("New watermark must not be null");
    }

    @Test
    @DisplayName("应该成功前进首次使用的空游标")
    void shouldAdvanceEmptyCursorForFirstTime() {
      // Given - 空水位线的游标
      Cursor cursor = CursorTestDataBuilder.aTimeCursor().watermark((Instant) null).buildRestored();

      // When - 首次前进
      Instant firstWatermark = Instant.parse("2024-01-01T00:00:00Z");
      cursor.advanceTo(firstWatermark);

      // Then
      assertThat(cursor.getWatermark().normalizedInstant()).isEqualTo(firstWatermark);
    }
  }

  @Nested
  @DisplayName("advanceTo() 方法 - 带血缘")
  class AdvanceToWithLineageMethodTests {

    @Test
    @DisplayName("应该成功前进水位线并更新血缘")
    void shouldAdvanceWatermarkAndUpdateLineage() {
      // Given
      Instant originalWatermark = Instant.parse("2024-01-01T00:00:00Z");
      CursorLineage originalLineage = new CursorLineage(1001L, 2001L, 3001L, 4001L, 5001L, 6001L);
      Cursor cursor =
          CursorTestDataBuilder.aTimeCursor()
              .watermark(originalWatermark)
              .lineage(originalLineage)
              .build();

      // When
      Instant newWatermark = Instant.parse("2024-02-01T00:00:00Z");
      CursorLineage newLineage = new CursorLineage(9001L, 9002L, 9003L, 9004L, 9005L, 9006L);
      cursor.advanceTo(newWatermark, newLineage);

      // Then
      assertThat(cursor.getWatermark().normalizedInstant()).isEqualTo(newWatermark);
      assertThat(cursor.getLineage()).isEqualTo(newLineage);
    }

    @Test
    @DisplayName("应该保持原血缘当新血缘为 null")
    void shouldKeepOriginalLineageWhenNewLineageIsNull() {
      // Given
      CursorLineage originalLineage = new CursorLineage(1001L, 2001L, 3001L, 4001L, 5001L, 6001L);
      Cursor cursor =
          CursorTestDataBuilder.aTimeCursor()
              .watermark(Instant.parse("2024-01-01T00:00:00Z"))
              .lineage(originalLineage)
              .build();

      // When - 不提供新血缘
      cursor.advanceTo(Instant.parse("2024-02-01T00:00:00Z"), null);

      // Then - 血缘应该保持不变
      assertThat(cursor.getLineage()).isEqualTo(originalLineage);
    }
  }

  @Nested
  @DisplayName("advanceTo() 方法 - 带表达式哈希追踪")
  class AdvanceToWithExprHashMethodTests {

    @Test
    @DisplayName("应该返回 SUCCESS 当表达式哈希匹配")
    void shouldReturnSuccessWhenExprHashMatches() {
      // Given
      String exprHash = "expr-hash-12345";
      Cursor cursor =
          CursorTestDataBuilder.aTimeCursor()
              .watermark(Instant.parse("2024-01-01T00:00:00Z"))
              .exprHash(exprHash)
              .build();

      // When - 使用相同的表达式哈希前进
      Instant newWatermark = Instant.parse("2024-02-01T00:00:00Z");
      Cursor.AdvancementResult result = cursor.advanceTo(newWatermark, null, exprHash);

      // Then
      assertThat(result).isEqualTo(Cursor.AdvancementResult.SUCCESS);
      assertThat(cursor.getWatermark().normalizedInstant()).isEqualTo(newWatermark);
      assertThat(cursor.getExprHash()).isEqualTo(exprHash);
    }

    @Test
    @DisplayName("应该返回 EXPRESSION_CHANGED 当表达式哈希不匹配")
    void shouldReturnExpressionChangedWhenHashMismatch() {
      // Given
      Cursor cursor =
          CursorTestDataBuilder.aTimeCursor()
              .watermark(Instant.parse("2024-01-01T00:00:00Z"))
              .exprHash("old-hash")
              .build();

      Instant originalWatermark = cursor.getWatermark().normalizedInstant();

      // When - 使用不同的表达式哈希前进
      String newExprHash = "new-hash";
      Cursor.AdvancementResult result =
          cursor.advanceTo(Instant.parse("2024-02-01T00:00:00Z"), null, newExprHash);

      // Then
      assertThat(result).isEqualTo(Cursor.AdvancementResult.EXPRESSION_CHANGED);
      // 水位线不应该变化
      assertThat(cursor.getWatermark().normalizedInstant()).isEqualTo(originalWatermark);
      // 表达式哈希不应该更新
      assertThat(cursor.getExprHash()).isEqualTo("old-hash");
    }

    @Test
    @DisplayName("应该返回 SUCCESS 当两个表达式哈希都为 null")
    void shouldReturnSuccessWhenBothHashesAreNull() {
      // Given - 无表达式哈希的游标
      Cursor cursor =
          CursorTestDataBuilder.aTimeCursor()
              .watermark(Instant.parse("2024-01-01T00:00:00Z"))
              .exprHash(null)
              .build();

      // When - 使用 null 表达式哈希前进
      Cursor.AdvancementResult result =
          cursor.advanceTo(Instant.parse("2024-02-01T00:00:00Z"), null, null);

      // Then
      assertThat(result).isEqualTo(Cursor.AdvancementResult.SUCCESS);
    }

    @Test
    @DisplayName("应该返回 EXPRESSION_CHANGED 当原哈希为 null 而新哈希不为 null")
    void shouldReturnExpressionChangedWhenOldHashIsNullAndNewIsNot() {
      // Given
      Cursor cursor =
          CursorTestDataBuilder.aTimeCursor()
              .watermark(Instant.parse("2024-01-01T00:00:00Z"))
              .exprHash(null)
              .build();

      // When
      Cursor.AdvancementResult result =
          cursor.advanceTo(Instant.parse("2024-02-01T00:00:00Z"), null, "new-hash");

      // Then
      assertThat(result).isEqualTo(Cursor.AdvancementResult.EXPRESSION_CHANGED);
    }

    @Test
    @DisplayName("应该返回 EXPRESSION_CHANGED 当原哈希不为 null 而新哈希为 null")
    void shouldReturnExpressionChangedWhenOldHashIsNotNullAndNewIsNull() {
      // Given
      Cursor cursor =
          CursorTestDataBuilder.aTimeCursor()
              .watermark(Instant.parse("2024-01-01T00:00:00Z"))
              .exprHash("old-hash")
              .build();

      // When
      Cursor.AdvancementResult result =
          cursor.advanceTo(Instant.parse("2024-02-01T00:00:00Z"), null, null);

      // Then
      assertThat(result).isEqualTo(Cursor.AdvancementResult.EXPRESSION_CHANGED);
    }

    @Test
    @DisplayName("应该更新表达式哈希当前进成功（哈希相同）")
    void shouldUpdateExprHashWhenAdvancementSucceeds() {
      // Given
      String exprHash = "expr-hash-12345";
      Cursor cursor =
          CursorTestDataBuilder.aTimeCursor()
              .watermark(Instant.parse("2024-01-01T00:00:00Z"))
              .exprHash(exprHash)
              .build();

      // When - 使用相同的哈希前进
      Cursor.AdvancementResult result =
          cursor.advanceTo(Instant.parse("2024-02-01T00:00:00Z"), null, exprHash);

      // Then
      assertThat(result).isEqualTo(Cursor.AdvancementResult.SUCCESS);
      assertThat(cursor.getExprHash()).isEqualTo(exprHash);
    }

    @Test
    @DisplayName("应该更新血缘当提供新血缘且前进成功")
    void shouldUpdateLineageWhenProvidedAndAdvancementSucceeds() {
      // Given
      Cursor cursor =
          CursorTestDataBuilder.aTimeCursor()
              .watermark(Instant.parse("2024-01-01T00:00:00Z"))
              .exprHash("hash")
              .build();

      // When
      CursorLineage newLineage = new CursorLineage(8001L, 8002L, 8003L, 8004L, 8005L, 8006L);
      Cursor.AdvancementResult result =
          cursor.advanceTo(Instant.parse("2024-02-01T00:00:00Z"), newLineage, "hash");

      // Then
      assertThat(result).isEqualTo(Cursor.AdvancementResult.SUCCESS);
      assertThat(cursor.getLineage()).isEqualTo(newLineage);
    }

    @Test
    @DisplayName("应该抛出异常当新水位线为 null（即使表达式哈希匹配）")
    void shouldThrowExceptionWhenNewWatermarkIsNullEvenIfHashMatches() {
      // Given
      Cursor cursor = CursorTestDataBuilder.aTimeCursor().exprHash("hash").build();

      // When & Then
      assertThatThrownBy(() -> cursor.advanceTo(null, null, "hash"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("New watermark must not be null");
    }

    @Test
    @DisplayName("应该在检查哈希之前验证水位线单调性")
    void shouldValidateWatermarkMonotonicityBeforeCheckingHash() {
      // Given
      Cursor cursor =
          CursorTestDataBuilder.aTimeCursor()
              .watermark(Instant.parse("2024-02-01T00:00:00Z"))
              .exprHash("hash")
              .build();

      // When & Then - 尝试回退水位线（即使哈希匹配）
      assertThatThrownBy(
              () -> cursor.advanceTo(Instant.parse("2024-01-01T00:00:00Z"), null, "hash"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Cursor watermark cannot move backwards");
    }
  }

  // ========== 表达式匹配测试 ==========

  @Nested
  @DisplayName("matchesExpression() 方法")
  class MatchesExpressionMethodTests {

    @Test
    @DisplayName("应该返回 true 当表达式哈希匹配")
    void shouldReturnTrueWhenExprHashMatches() {
      // Given
      String exprHash = "expr-hash-12345";
      Cursor cursor = CursorTestDataBuilder.aTimeCursor().exprHash(exprHash).build();

      // When & Then
      assertThat(cursor.matchesExpression(exprHash)).isTrue();
    }

    @Test
    @DisplayName("应该返回 false 当表达式哈希不匹配")
    void shouldReturnFalseWhenExprHashDoesNotMatch() {
      // Given
      Cursor cursor = CursorTestDataBuilder.aTimeCursor().exprHash("old-hash").build();

      // When & Then
      assertThat(cursor.matchesExpression("new-hash")).isFalse();
    }

    @Test
    @DisplayName("应该返回 true 当两个表达式哈希都为 null")
    void shouldReturnTrueWhenBothHashesAreNull() {
      // Given
      Cursor cursor = CursorTestDataBuilder.aTimeCursor().exprHash(null).build();

      // When & Then
      assertThat(cursor.matchesExpression(null)).isTrue();
    }

    @Test
    @DisplayName("应该返回 false 当当前哈希为 null 而比较哈希不为 null")
    void shouldReturnFalseWhenCurrentHashIsNullAndComparedIsNot() {
      // Given
      Cursor cursor = CursorTestDataBuilder.aTimeCursor().exprHash(null).build();

      // When & Then
      assertThat(cursor.matchesExpression("some-hash")).isFalse();
    }

    @Test
    @DisplayName("应该返回 false 当当前哈希不为 null 而比较哈希为 null")
    void shouldReturnFalseWhenCurrentHashIsNotNullAndComparedIsNull() {
      // Given
      Cursor cursor = CursorTestDataBuilder.aTimeCursor().exprHash("some-hash").build();

      // When & Then
      assertThat(cursor.matchesExpression(null)).isFalse();
    }
  }

  // ========== 更新观察最大值测试 ==========

  @Nested
  @DisplayName("updateObservedMax() 方法")
  class UpdateObservedMaxMethodTests {

    @Test
    @DisplayName("应该更新观察到的最大值")
    void shouldUpdateObservedMaxValue() {
      // Given
      Instant originalWatermark = Instant.parse("2024-01-01T00:00:00Z");
      Cursor cursor = CursorTestDataBuilder.aTimeCursor().watermark(originalWatermark).build();

      // When
      String newObservedMax = "2024-01-15T12:30:45Z";
      cursor.updateObservedMax(newObservedMax);

      // Then
      assertThat(cursor.getWatermark().observedMaxValue()).isEqualTo(newObservedMax);
      // 规范化的时间戳和数值应该保持不变
      assertThat(cursor.getWatermark().normalizedInstant()).isEqualTo(originalWatermark);
      assertThat(cursor.getWatermark().normalizedNumeric()).isNull();
    }

    @Test
    @DisplayName("应该保留规范化值当更新观察最大值")
    void shouldPreserveNormalizedValuesWhenUpdatingObservedMax() {
      // Given
      Instant normalizedInstant = Instant.parse("2024-01-01T00:00:00Z");
      BigDecimal normalizedNumeric = new BigDecimal("12345");
      Cursor cursor =
          CursorTestDataBuilder.aTimeCursor()
              .watermark(new CursorWatermark("original", normalizedInstant, normalizedNumeric))
              .buildRestored();

      // When
      cursor.updateObservedMax("new-observed-max");

      // Then
      assertThat(cursor.getWatermark().observedMaxValue()).isEqualTo("new-observed-max");
      assertThat(cursor.getWatermark().normalizedInstant()).isEqualTo(normalizedInstant);
      assertThat(cursor.getWatermark().normalizedNumeric()).isEqualTo(normalizedNumeric);
    }
  }

  // ========== 获取当前水位线测试 ==========

  @Nested
  @DisplayName("getCurrentWatermark() 方法")
  class GetCurrentWatermarkMethodTests {

    @Test
    @DisplayName("应该返回当前时间水位线")
    void shouldReturnCurrentTimeWatermark() {
      // Given
      Instant watermark = Instant.parse("2024-01-01T00:00:00Z");
      Cursor cursor = CursorTestDataBuilder.aTimeCursor().watermark(watermark).build();

      // When
      Instant currentWatermark = cursor.getCurrentWatermark();

      // Then
      assertThat(currentWatermark).isEqualTo(watermark);
    }

    @Test
    @DisplayName("应该返回 null 当水位线为空")
    void shouldReturnNullWhenWatermarkIsEmpty() {
      // Given
      Cursor cursor = CursorTestDataBuilder.aTimeCursor().watermark((Instant) null).buildRestored();

      // When
      Instant currentWatermark = cursor.getCurrentWatermark();

      // Then
      assertThat(currentWatermark).isNull();
    }
  }

  // ========== 命名空间范围测试 ==========

  @Nested
  @DisplayName("命名空间范围测试")
  class NamespaceScopeTests {

    @Test
    @DisplayName("应该支持 GLOBAL 命名空间范围")
    void shouldSupportGlobalNamespace() {
      // Given & When
      Cursor cursor =
          Cursor.create(
              ProvenanceCode.PUBMED,
              "HARVEST",
              "publication_date",
              "GLOBAL",
              "global",
              Instant.parse("2024-01-01T00:00:00Z"));

      // Then
      assertThat(cursor.getNamespaceScope()).isEqualTo(NamespaceScope.GLOBAL);
      assertThat(cursor.getNamespaceKey()).isEqualTo("global");
    }

    @Test
    @DisplayName("应该支持 EXPR 命名空间范围")
    void shouldSupportExprNamespace() {
      // Given & When
      Cursor cursor =
          Cursor.create(
              ProvenanceCode.PUBMED,
              "HARVEST",
              "publication_date",
              "EXPR",
              "expr-hash-12345",
              Instant.parse("2024-01-01T00:00:00Z"));

      // Then
      assertThat(cursor.getNamespaceScope()).isEqualTo(NamespaceScope.EXPR);
      assertThat(cursor.getNamespaceKey()).isEqualTo("expr-hash-12345");
    }

    @Test
    @DisplayName("应该支持 CUSTOM 命名空间范围")
    void shouldSupportCustomNamespace() {
      // Given & When
      Cursor cursor =
          Cursor.create(
              ProvenanceCode.PUBMED,
              "HARVEST",
              "publication_date",
              "CUSTOM",
              "custom-namespace-001",
              Instant.parse("2024-01-01T00:00:00Z"));

      // Then
      assertThat(cursor.getNamespaceScope()).isEqualTo(NamespaceScope.CUSTOM);
      assertThat(cursor.getNamespaceKey()).isEqualTo("custom-namespace-001");
    }

    @Test
    @DisplayName("应该正确处理小写命名空间范围代码")
    void shouldHandleLowercaseNamespaceScopeCode() {
      // Given & When - 使用小写
      Cursor cursor =
          Cursor.create(
              ProvenanceCode.PUBMED,
              "HARVEST",
              "publication_date",
              "global", // 小写
              "global",
              Instant.parse("2024-01-01T00:00:00Z"));

      // Then
      assertThat(cursor.getNamespaceScope()).isEqualTo(NamespaceScope.GLOBAL);
    }
  }

  // ========== 边界条件测试 ==========

  @Nested
  @DisplayName("边界条件测试")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该处理极端时间边界")
    void shouldHandleExtremeTimeBoundaries() {
      // Given - Unix Epoch
      Instant epoch = Instant.parse("1970-01-01T00:00:00Z");

      // When
      Cursor cursor =
          Cursor.create(
              ProvenanceCode.PUBMED, "HARVEST", "publication_date", "GLOBAL", "global", epoch);

      // Then
      assertThat(cursor.getWatermark().normalizedInstant()).isEqualTo(epoch);
    }

    @Test
    @DisplayName("应该处理远期时间")
    void shouldHandleFutureTime() {
      // Given - 远期时间
      Instant future = Instant.parse("2099-12-31T23:59:59Z");

      // When
      Cursor cursor =
          Cursor.create(
              ProvenanceCode.PUBMED, "HARVEST", "publication_date", "GLOBAL", "global", future);

      // Then
      assertThat(cursor.getWatermark().normalizedInstant()).isEqualTo(future);
    }

    @Test
    @DisplayName("应该处理极长的字符串字段")
    void shouldHandleVeryLongStringFields() {
      // Given
      // ProvenanceCode 是枚举，使用标准值
      ProvenanceCode provenanceCode = ProvenanceCode.PUBMED;
      String longCursorKey = "b".repeat(500);
      String longNamespaceKey = "c".repeat(500);

      // When
      Cursor cursor =
          Cursor.create(
              provenanceCode,
              "HARVEST",
              longCursorKey,
              "GLOBAL",
              longNamespaceKey,
              Instant.parse("2024-01-01T00:00:00Z"));

      // Then
      assertThat(cursor.getProvenanceCode()).isEqualTo(provenanceCode);
      assertThat(cursor.getCursorKey()).hasSize(500);
      assertThat(cursor.getNamespaceKey()).hasSize(500);
    }

    @Test
    @DisplayName("应该处理连续的快速前进")
    void shouldHandleRapidSuccessiveAdvancements() {
      // Given
      Cursor cursor =
          CursorTestDataBuilder.aTimeCursor()
              .watermark(Instant.parse("2024-01-01T00:00:00Z"))
              .build();

      // When - 连续 10 次前进
      for (int i = 1; i <= 10; i++) {
        Instant nextWatermark = Instant.parse("2024-01-01T00:00:00Z").plusSeconds(i);
        cursor.advanceTo(nextWatermark);
      }

      // Then
      assertThat(cursor.getWatermark().normalizedInstant())
          .isEqualTo(Instant.parse("2024-01-01T00:00:10Z"));
    }

    @Test
    @DisplayName("应该处理毫秒级时间精度")
    void shouldHandleMillisecondTimePrecision() {
      // Given
      Instant preciseTime = Instant.parse("2024-01-01T12:30:45.123456789Z");

      // When
      Cursor cursor =
          Cursor.create(
              ProvenanceCode.PUBMED,
              "HARVEST",
              "publication_date",
              "GLOBAL",
              "global",
              preciseTime);

      // Then
      assertThat(cursor.getWatermark().normalizedInstant()).isEqualTo(preciseTime);
    }
  }

  // ========== 业务规则测试 ==========

  @Nested
  @DisplayName("业务规则测试")
  class BusinessRuleTests {

    @Test
    @DisplayName("应该确保游标唯一性标识符的完整性")
    void shouldEnsureUniquenessIdentifierIntegrity() {
      // Given
      ProvenanceCode provenanceCode = ProvenanceCode.PUBMED;
      String operationCode = "HARVEST";
      String cursorKey = "publication_date";
      String namespaceScope = "GLOBAL";
      String namespaceKey = "global";

      // When
      Cursor cursor =
          Cursor.create(
              provenanceCode,
              operationCode,
              cursorKey,
              namespaceScope,
              namespaceKey,
              Instant.parse("2024-01-01T00:00:00Z"));

      // Then - 所有唯一性标识字段应该被正确设置
      assertThat(cursor.getProvenanceCode()).isEqualTo(provenanceCode);
      assertThat(cursor.getOperationCode()).isEqualTo(operationCode);
      assertThat(cursor.getCursorKey()).isEqualTo(cursorKey);
      assertThat(cursor.getNamespaceScope()).isEqualTo(NamespaceScope.GLOBAL);
      assertThat(cursor.getNamespaceKey()).isEqualTo(namespaceKey);
    }

    @Test
    @DisplayName("应该在生命周期中保持不可变字段")
    void shouldPreserveImmutableFieldsThroughLifecycle() {
      // Given
      ProvenanceCode provenanceCode = ProvenanceCode.PUBMED;
      String operationCode = "HARVEST";
      String cursorKey = "publication_date";

      Cursor cursor =
          CursorTestDataBuilder.aTimeCursor()
              .provenanceCode(provenanceCode)
              .operationCode(operationCode)
              .cursorKey(cursorKey)
              .build();

      // When - 执行各种操作
      cursor.advanceTo(Instant.parse("2024-02-01T00:00:00Z"));
      cursor.updateObservedMax("new-max");
      cursor.advance(
          CursorValue.time(Instant.parse("2024-03-01T00:00:00Z")),
          null,
          new CursorLineage(1L, 2L, 3L, 4L, 5L, 6L),
          "new-hash");

      // Then - 不可变字段应该保持不变
      assertThat(cursor.getProvenanceCode()).isEqualTo(provenanceCode);
      assertThat(cursor.getOperationCode()).isEqualTo(operationCode);
      assertThat(cursor.getCursorKey()).isEqualTo(cursorKey);
    }

    @Test
    @DisplayName("应该支持表达式哈希变更导致的游标重置场景")
    void shouldSupportCursorResetOnExpressionHashChange() {
      // Given - 现有游标带旧哈希
      Cursor cursor =
          CursorTestDataBuilder.aTimeCursor()
              .watermark(Instant.parse("2024-06-01T00:00:00Z"))
              .exprHash("old-expr-hash")
              .build();

      // When - 检测到表达式变更
      Cursor.AdvancementResult result =
          cursor.advanceTo(Instant.parse("2024-07-01T00:00:00Z"), null, "new-expr-hash");

      // Then - 应该返回 EXPRESSION_CHANGED
      assertThat(result).isEqualTo(Cursor.AdvancementResult.EXPRESSION_CHANGED);
      // 调用方应该基于此结果创建新游标或重置现有游标
    }

    @Test
    @DisplayName("应该正确处理血缘完整性")
    void shouldHandleLineageIntegrity() {
      // Given
      CursorLineage lineage = new CursorLineage(1001L, 2001L, 3001L, 4001L, 5001L, 6001L);
      Cursor cursor = CursorTestDataBuilder.aTimeCursor().lineage(lineage).build();

      // When & Then - 血缘应该完整保留
      assertThat(cursor.getLineage().scheduleInstanceId()).isEqualTo(1001L);
      assertThat(cursor.getLineage().planId()).isEqualTo(2001L);
      assertThat(cursor.getLineage().sliceId()).isEqualTo(3001L);
      assertThat(cursor.getLineage().taskId()).isEqualTo(4001L);
      assertThat(cursor.getLineage().runId()).isEqualTo(5001L);
      assertThat(cursor.getLineage().batchId()).isEqualTo(6001L);
    }
  }

  // ========== TestDataBuilder (辅助类) ==========

  /// Cursor 测试数据构建器。
  ///
  /// 遵循 Builder 模式，提供默认值以简化测试数据构建。
  static class CursorTestDataBuilder {
    private Long id = null;
    private ProvenanceCode provenanceCode = ProvenanceCode.PUBMED;
    private String operationCode = "HARVEST";
    private String cursorKey = "publication_date";
    private NamespaceScope namespaceScope = NamespaceScope.GLOBAL;
    private String namespaceKey = "global";
    private CursorType cursorType = CursorType.TIME;
    private CursorValue value = CursorValue.time(Instant.parse("2024-01-01T00:00:00Z"));
    private CursorWatermark watermark =
        new CursorWatermark("2024-01-01T00:00:00Z", Instant.parse("2024-01-01T00:00:00Z"), null);
    private CursorLineage lineage = CursorLineage.empty();
    private String exprHash = null;

    public static CursorTestDataBuilder aTimeCursor() {
      return new CursorTestDataBuilder();
    }

    public CursorTestDataBuilder id(Long id) {
      this.id = id;
      return this;
    }

    public CursorTestDataBuilder provenanceCode(ProvenanceCode provenanceCode) {
      this.provenanceCode = provenanceCode;
      return this;
    }

    public CursorTestDataBuilder provenanceCode(String provenanceCode) {
      this.provenanceCode = ProvenanceCode.parse(provenanceCode);
      return this;
    }

    public CursorTestDataBuilder operationCode(String operationCode) {
      this.operationCode = operationCode;
      return this;
    }

    public CursorTestDataBuilder cursorKey(String cursorKey) {
      this.cursorKey = cursorKey;
      return this;
    }

    public CursorTestDataBuilder namespaceScope(NamespaceScope namespaceScope) {
      this.namespaceScope = namespaceScope;
      return this;
    }

    public CursorTestDataBuilder namespaceKey(String namespaceKey) {
      this.namespaceKey = namespaceKey;
      return this;
    }

    public CursorTestDataBuilder cursorType(CursorType cursorType) {
      this.cursorType = cursorType;
      return this;
    }

    public CursorTestDataBuilder value(CursorValue value) {
      this.value = value;
      return this;
    }

    public CursorTestDataBuilder watermark(Instant watermark) {
      if (watermark == null) {
        this.watermark = CursorWatermark.empty();
        this.value = CursorValue.empty();
      } else {
        this.watermark = new CursorWatermark(watermark.toString(), watermark, null);
        this.value = CursorValue.time(watermark);
      }
      return this;
    }

    public CursorTestDataBuilder watermark(CursorWatermark watermark) {
      this.watermark = watermark;
      return this;
    }

    public CursorTestDataBuilder lineage(CursorLineage lineage) {
      this.lineage = lineage;
      return this;
    }

    public CursorTestDataBuilder exprHash(String exprHash) {
      this.exprHash = exprHash;
      return this;
    }

    /// 构建新创建的游标（使用 create() 工厂方法）。
    public Cursor build() {
      return Cursor.create(
          provenanceCode,
          operationCode,
          cursorKey,
          namespaceScope.getCode(),
          namespaceKey,
          watermark.normalizedInstant(),
          lineage,
          exprHash);
    }

    /// 构建从持久化重建的游标（使用 restore() 工厂方法）。
    public Cursor buildRestored() {
      Long restoredId = (id != null) ? id : 100L;
      return Cursor.restore(
          restoredId,
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
  }
}
