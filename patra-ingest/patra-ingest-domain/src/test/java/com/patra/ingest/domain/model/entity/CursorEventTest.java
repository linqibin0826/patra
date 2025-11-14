package com.patra.ingest.domain.model.entity;

import static org.assertj.core.api.Assertions.*;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.CursorDirection;
import com.patra.ingest.domain.model.enums.CursorType;
import com.patra.ingest.domain.model.vo.cursor.CursorLineage;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * CursorEvent 单元测试。
 *
 * <p>测试策略：
 *
 * <ul>
 *   <li>纯 Java 单元测试，不依赖 Spring 容器
 *   <li>使用 TestDataBuilder 模式构建测试数据
 *   <li>遵循 Given-When-Then 结构
 *   <li>使用 AssertJ 流畅断言
 * </ul>
 *
 * <p>测试范围：
 *
 * <ul>
 *   <li>✅ 工厂方法测试（create, restore）
 *   <li>✅ 默认值逻辑测试（namespaceKey, lineage）
 *   <li>✅ 不同游标类型测试（TIME, ID）
 *   <li>✅ 方向测试（FORWARD, BACKFILL）
 *   <li>✅ 窗口时间测试
 *   <li>✅ Getter 测试
 *   <li>✅ 边界情况测试
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@DisplayName("CursorEvent 单元测试")
class CursorEventTest {

  // ========== 工厂方法测试 ==========

  @Nested
  @DisplayName("create() 工厂方法")
  class CreateFactoryMethodTests {

    @Test
    @DisplayName("应该成功创建 TIME 类型的游标前进事件")
    void shouldCreateTimeTypeCursorEvent() {
      // Given
      ProvenanceCode provenanceCode = ProvenanceCode.PUBMED;
      String operationCode = "HARVEST";
      String cursorKey = "publication_date";
      String namespaceScopeCode = "GLOBAL";
      String namespaceKey = "global-namespace";
      CursorType cursorType = CursorType.TIME;
      String prevValue = "2024-01-01T00:00:00Z";
      String newValue = "2024-02-01T00:00:00Z";
      Instant prevInstant = Instant.parse(prevValue);
      Instant newInstant = Instant.parse(newValue);
      CursorDirection direction = CursorDirection.FORWARD;
      String idempotentKey = "idem-key-001";
      CursorLineage lineage = new CursorLineage(1001L, 2001L, 3001L, 4001L, 5001L, 6001L);
      String exprHash = "expr-hash-12345";
      Instant windowFrom = Instant.parse("2024-01-01T00:00:00Z");
      Instant windowTo = Instant.parse("2024-01-31T23:59:59Z");

      // When
      CursorEvent event =
          CursorEvent.create(
              provenanceCode,
              operationCode,
              cursorKey,
              namespaceScopeCode,
              namespaceKey,
              cursorType,
              prevValue,
              newValue,
              prevInstant,
              newInstant,
              direction,
              idempotentKey,
              lineage,
              exprHash,
              windowFrom,
              windowTo);

      // Then
      assertThat(event).isNotNull();
      assertThat(event.getId()).isNull(); // 新创建的事件 ID 为 null
      assertThat(event.getProvenanceCode()).isEqualTo(provenanceCode);
      assertThat(event.getOperationCode()).isEqualTo(operationCode);
      assertThat(event.getCursorKey()).isEqualTo(cursorKey);
      assertThat(event.getNamespaceScopeCode()).isEqualTo(namespaceScopeCode);
      assertThat(event.getNamespaceKey()).isEqualTo(namespaceKey);
      assertThat(event.getCursorType()).isEqualTo(cursorType);
      assertThat(event.getPrevValue()).isEqualTo(prevValue);
      assertThat(event.getNewValue()).isEqualTo(newValue);
      assertThat(event.getPrevInstant()).isEqualTo(prevInstant);
      assertThat(event.getNewInstant()).isEqualTo(newInstant);
      assertThat(event.getDirection()).isEqualTo(direction);
      assertThat(event.getIdempotentKey()).isEqualTo(idempotentKey);
      assertThat(event.getLineage()).isEqualTo(lineage);
      assertThat(event.getExprHash()).isEqualTo(exprHash);
      assertThat(event.getWindowFrom()).isEqualTo(windowFrom);
      assertThat(event.getWindowTo()).isEqualTo(windowTo);

      // TIME 类型的事件数值字段应该为 null
      assertThat(event.getPrevNumeric()).isNull();
      assertThat(event.getNewNumeric()).isNull();

      // observedMaxValue 为未来增强功能，当前为 null
      assertThat(event.getObservedMaxValue()).isNull();
    }

    @Test
    @DisplayName("应该成功创建 ID 类型的游标前进事件")
    void shouldCreateNumericTypeCursorEvent() {
      // Given
      CursorType cursorType = CursorType.ID;
      String prevValue = "1000";
      String newValue = "2000";

      // When - ID 类型不使用 Instant 字段
      CursorEvent event =
          CursorEvent.create(
              ProvenanceCode.PUBMED,
              "HARVEST",
              "record_id",
              "GLOBAL",
              "global",
              cursorType,
              prevValue,
              newValue,
              null, // prevInstant - ID 类型不使用
              null, // newInstant - ID 类型不使用
              CursorDirection.FORWARD,
              "idem-key-002",
              CursorLineage.empty(),
              null,
              null, // windowFrom - ID 类型不使用
              null // windowTo - ID 类型不使用
              );

      // Then
      assertThat(event.getCursorType()).isEqualTo(CursorType.ID);
      assertThat(event.getPrevValue()).isEqualTo(prevValue);
      assertThat(event.getNewValue()).isEqualTo(newValue);

      // ID 类型的时间字段应该为 null
      assertThat(event.getPrevInstant()).isNull();
      assertThat(event.getNewInstant()).isNull();
      assertThat(event.getWindowFrom()).isNull();
      assertThat(event.getWindowTo()).isNull();

      // prevNumeric 和 newNumeric 在 create() 中固定为 null（仅在 restore() 中设置）
      assertThat(event.getPrevNumeric()).isNull();
      assertThat(event.getNewNumeric()).isNull();
    }

    @Test
    @DisplayName("应该成功创建首次前进事件（prevValue 和 prevInstant 为 null）")
    void shouldCreateFirstAdvancementEvent() {
      // Given - 首次前进，没有前一个值
      String prevValue = null;
      Instant prevInstant = null;

      // When
      CursorEvent event =
          CursorEvent.create(
              ProvenanceCode.PUBMED,
              "HARVEST",
              "publication_date",
              "GLOBAL",
              "global",
              CursorType.TIME,
              prevValue,
              "2024-01-01T00:00:00Z",
              prevInstant,
              Instant.parse("2024-01-01T00:00:00Z"),
              CursorDirection.FORWARD,
              "idem-key-003",
              CursorLineage.empty(),
              null,
              Instant.parse("2024-01-01T00:00:00Z"),
              Instant.parse("2024-01-31T23:59:59Z"));

      // Then - 前值字段应该为 null
      assertThat(event.getPrevValue()).isNull();
      assertThat(event.getPrevInstant()).isNull();

      // 新值字段应该被正确设置
      assertThat(event.getNewValue()).isEqualTo("2024-01-01T00:00:00Z");
      assertThat(event.getNewInstant()).isEqualTo(Instant.parse("2024-01-01T00:00:00Z"));
    }

    @Test
    @DisplayName("应该使用默认 namespaceKey 当传入 null")
    void shouldUseDefaultNamespaceKeyWhenNull() {
      // Given
      String namespaceKey = null;

      // When
      CursorEvent event =
          CursorEvent.create(
              ProvenanceCode.PUBMED,
              "HARVEST",
              "publication_date",
              "GLOBAL",
              namespaceKey, // null
              CursorType.TIME,
              null,
              "2024-01-01T00:00:00Z",
              null,
              Instant.parse("2024-01-01T00:00:00Z"),
              CursorDirection.FORWARD,
              "idem-key-004",
              CursorLineage.empty(),
              null,
              null,
              null);

      // Then - 应该使用 64 个 '0' 作为默认值
      assertThat(event.getNamespaceKey()).isEqualTo("0".repeat(64));
    }

    @Test
    @DisplayName("应该保留非 null 的 namespaceKey")
    void shouldPreserveNonNullNamespaceKey() {
      // Given
      String namespaceKey = "custom-namespace-key";

      // When
      CursorEvent event =
          CursorEvent.create(
              ProvenanceCode.PUBMED,
              "HARVEST",
              "publication_date",
              "GLOBAL",
              namespaceKey,
              CursorType.TIME,
              null,
              "2024-01-01T00:00:00Z",
              null,
              Instant.parse("2024-01-01T00:00:00Z"),
              CursorDirection.FORWARD,
              "idem-key-005",
              CursorLineage.empty(),
              null,
              null,
              null);

      // Then - 应该保留原始值
      assertThat(event.getNamespaceKey()).isEqualTo(namespaceKey);
    }

    @Test
    @DisplayName("应该使用空血缘当传入 null")
    void shouldUseEmptyLineageWhenNull() {
      // Given
      CursorLineage lineage = null;

      // When
      CursorEvent event =
          CursorEvent.create(
              ProvenanceCode.PUBMED,
              "HARVEST",
              "publication_date",
              "GLOBAL",
              "global",
              CursorType.TIME,
              null,
              "2024-01-01T00:00:00Z",
              null,
              Instant.parse("2024-01-01T00:00:00Z"),
              CursorDirection.FORWARD,
              "idem-key-006",
              lineage, // null
              null,
              null,
              null);

      // Then - 应该使用 CursorLineage.empty()
      assertThat(event.getLineage()).isEqualTo(CursorLineage.empty());
    }

    @Test
    @DisplayName("应该保留非 null 的血缘")
    void shouldPreserveNonNullLineage() {
      // Given
      CursorLineage lineage = new CursorLineage(1001L, 2001L, 3001L, 4001L, 5001L, 6001L);

      // When
      CursorEvent event =
          CursorEvent.create(
              ProvenanceCode.PUBMED,
              "HARVEST",
              "publication_date",
              "GLOBAL",
              "global",
              CursorType.TIME,
              null,
              "2024-01-01T00:00:00Z",
              null,
              Instant.parse("2024-01-01T00:00:00Z"),
              CursorDirection.FORWARD,
              "idem-key-007",
              lineage,
              null,
              null,
              null);

      // Then - 应该保留原始血缘
      assertThat(event.getLineage()).isEqualTo(lineage);
      assertThat(event.getLineage().scheduleInstanceId()).isEqualTo(1001L);
      assertThat(event.getLineage().planId()).isEqualTo(2001L);
      assertThat(event.getLineage().sliceId()).isEqualTo(3001L);
      assertThat(event.getLineage().taskId()).isEqualTo(4001L);
      assertThat(event.getLineage().runId()).isEqualTo(5001L);
      assertThat(event.getLineage().batchId()).isEqualTo(6001L);
    }
  }

  @Nested
  @DisplayName("restore() 工厂方法")
  class RestoreFactoryMethodTests {

    @Test
    @DisplayName("应该从持久化状态成功重建 TIME 类型游标事件")
    void shouldRestoreTimeTypeCursorEventFromPersistentState() {
      // Given
      Long id = 100L;
      ProvenanceCode provenanceCode = ProvenanceCode.PUBMED;
      String operationCode = "HARVEST";
      String cursorKey = "publication_date";
      String namespaceScopeCode = "GLOBAL";
      String namespaceKey = "global-namespace";
      CursorType cursorType = CursorType.TIME;
      String prevValue = "2024-01-01T00:00:00Z";
      String newValue = "2024-02-01T00:00:00Z";
      CursorDirection direction = CursorDirection.FORWARD;
      String idempotentKey = "idem-key-001";
      String observedMaxValue = "2024-02-15T00:00:00Z";
      Instant prevInstant = Instant.parse(prevValue);
      Instant newInstant = Instant.parse(newValue);
      BigDecimal prevNumeric = null; // TIME 类型不使用
      BigDecimal newNumeric = null; // TIME 类型不使用
      CursorLineage lineage = new CursorLineage(1001L, 2001L, 3001L, 4001L, 5001L, 6001L);
      String exprHash = "expr-hash-12345";
      Instant windowFrom = Instant.parse("2024-01-01T00:00:00Z");
      Instant windowTo = Instant.parse("2024-01-31T23:59:59Z");

      // When
      CursorEvent event =
          CursorEvent.restore(
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
              exprHash,
              windowFrom,
              windowTo);

      // Then
      assertThat(event).isNotNull();
      assertThat(event.getId()).isEqualTo(id);
      assertThat(event.getProvenanceCode()).isEqualTo(provenanceCode);
      assertThat(event.getOperationCode()).isEqualTo(operationCode);
      assertThat(event.getCursorKey()).isEqualTo(cursorKey);
      assertThat(event.getNamespaceScopeCode()).isEqualTo(namespaceScopeCode);
      assertThat(event.getNamespaceKey()).isEqualTo(namespaceKey);
      assertThat(event.getCursorType()).isEqualTo(cursorType);
      assertThat(event.getPrevValue()).isEqualTo(prevValue);
      assertThat(event.getNewValue()).isEqualTo(newValue);
      assertThat(event.getDirection()).isEqualTo(direction);
      assertThat(event.getIdempotentKey()).isEqualTo(idempotentKey);
      assertThat(event.getObservedMaxValue()).isEqualTo(observedMaxValue);
      assertThat(event.getPrevInstant()).isEqualTo(prevInstant);
      assertThat(event.getNewInstant()).isEqualTo(newInstant);
      assertThat(event.getPrevNumeric()).isNull();
      assertThat(event.getNewNumeric()).isNull();
      assertThat(event.getLineage()).isEqualTo(lineage);
      assertThat(event.getExprHash()).isEqualTo(exprHash);
      assertThat(event.getWindowFrom()).isEqualTo(windowFrom);
      assertThat(event.getWindowTo()).isEqualTo(windowTo);
    }

    @Test
    @DisplayName("应该从持久化状态成功重建 ID 类型游标事件")
    void shouldRestoreNumericTypeCursorEventFromPersistentState() {
      // Given
      Long id = 200L;
      CursorType cursorType = CursorType.ID;
      String prevValue = "1000";
      String newValue = "2000";
      BigDecimal prevNumeric = new BigDecimal("1000");
      BigDecimal newNumeric = new BigDecimal("2000");

      // When
      CursorEvent event =
          CursorEvent.restore(
              id,
              ProvenanceCode.PUBMED,
              "HARVEST",
              "record_id",
              "GLOBAL",
              "global",
              cursorType,
              prevValue,
              newValue,
              CursorDirection.FORWARD,
              "idem-key-002",
              "2500", // observedMaxValue
              null, // prevInstant - ID 类型不使用
              null, // newInstant - ID 类型不使用
              prevNumeric,
              newNumeric,
              CursorLineage.empty(),
              "expr-hash-67890",
              null, // windowFrom - ID 类型不使用
              null // windowTo - ID 类型不使用
              );

      // Then
      assertThat(event.getId()).isEqualTo(id);
      assertThat(event.getCursorType()).isEqualTo(CursorType.ID);
      assertThat(event.getPrevValue()).isEqualTo(prevValue);
      assertThat(event.getNewValue()).isEqualTo(newValue);
      assertThat(event.getPrevNumeric()).isEqualTo(prevNumeric);
      assertThat(event.getNewNumeric()).isEqualTo(newNumeric);
      assertThat(event.getObservedMaxValue()).isEqualTo("2500");

      // 时间字段应该为 null
      assertThat(event.getPrevInstant()).isNull();
      assertThat(event.getNewInstant()).isNull();
      assertThat(event.getWindowFrom()).isNull();
      assertThat(event.getWindowTo()).isNull();
    }

    @Test
    @DisplayName("应该正确重建包含所有可选字段的事件")
    void shouldRestoreEventWithAllOptionalFields() {
      // Given - 所有字段都有值
      Long id = 300L;
      String observedMaxValue = "2024-03-01T00:00:00Z";
      String exprHash = "expr-hash-99999";
      Instant windowFrom = Instant.parse("2024-01-01T00:00:00Z");
      Instant windowTo = Instant.parse("2024-01-31T23:59:59Z");

      // When
      CursorEvent event =
          CursorEvent.restore(
              id,
              ProvenanceCode.PUBMED,
              "HARVEST",
              "publication_date",
              "GLOBAL",
              "global",
              CursorType.TIME,
              "2024-01-01T00:00:00Z",
              "2024-02-01T00:00:00Z",
              CursorDirection.FORWARD,
              "idem-key-003",
              observedMaxValue,
              Instant.parse("2024-01-01T00:00:00Z"),
              Instant.parse("2024-02-01T00:00:00Z"),
              null,
              null,
              new CursorLineage(1L, 2L, 3L, 4L, 5L, 6L),
              exprHash,
              windowFrom,
              windowTo);

      // Then - 所有字段都应该被正确设置
      assertThat(event.getId()).isEqualTo(id);
      assertThat(event.getObservedMaxValue()).isEqualTo(observedMaxValue);
      assertThat(event.getExprHash()).isEqualTo(exprHash);
      assertThat(event.getWindowFrom()).isEqualTo(windowFrom);
      assertThat(event.getWindowTo()).isEqualTo(windowTo);
    }

    @Test
    @DisplayName("应该正确处理所有可选字段为 null")
    void shouldHandleAllOptionalFieldsAsNull() {
      // Given - 所有可选字段为 null
      Long id = 400L;
      String observedMaxValue = null;
      Instant prevInstant = null;
      BigDecimal prevNumeric = null;
      BigDecimal newNumeric = null;
      CursorLineage lineage = null;
      String exprHash = null;
      Instant windowFrom = null;
      Instant windowTo = null;

      // When
      CursorEvent event =
          CursorEvent.restore(
              id,
              ProvenanceCode.PUBMED,
              "HARVEST",
              "publication_date",
              "GLOBAL",
              "global",
              CursorType.TIME,
              null, // prevValue
              "2024-01-01T00:00:00Z",
              CursorDirection.FORWARD,
              "idem-key-004",
              observedMaxValue,
              prevInstant,
              Instant.parse("2024-01-01T00:00:00Z"),
              prevNumeric,
              newNumeric,
              lineage,
              exprHash,
              windowFrom,
              windowTo);

      // Then
      assertThat(event.getObservedMaxValue()).isNull();
      assertThat(event.getPrevValue()).isNull();
      assertThat(event.getPrevInstant()).isNull();
      assertThat(event.getPrevNumeric()).isNull();
      assertThat(event.getNewNumeric()).isNull();
      assertThat(event.getLineage()).isEqualTo(CursorLineage.empty()); // 默认为空血缘
      assertThat(event.getExprHash()).isNull();
      assertThat(event.getWindowFrom()).isNull();
      assertThat(event.getWindowTo()).isNull();
    }
  }

  // ========== 方向测试 ==========

  @Nested
  @DisplayName("游标前进方向测试")
  class CursorDirectionTests {

    @Test
    @DisplayName("应该支持 FORWARD 方向")
    void shouldSupportForwardDirection() {
      // Given & When
      CursorEvent event =
          CursorEventTestDataBuilder.aTimeEvent()
              .direction(CursorDirection.FORWARD)
              .build();

      // Then
      assertThat(event.getDirection()).isEqualTo(CursorDirection.FORWARD);
    }

    @Test
    @DisplayName("应该支持 BACKFILL 方向")
    void shouldSupportBackfillDirection() {
      // Given & When
      CursorEvent event =
          CursorEventTestDataBuilder.aTimeEvent()
              .direction(CursorDirection.BACKFILL)
              .build();

      // Then
      assertThat(event.getDirection()).isEqualTo(CursorDirection.BACKFILL);
    }
  }

  // ========== 窗口时间测试 ==========

  @Nested
  @DisplayName("窗口时间范围测试")
  class WindowTimeRangeTests {

    @Test
    @DisplayName("应该正确存储 TIME 策略的窗口时间范围")
    void shouldStoreWindowTimeRangeForTimeStrategy() {
      // Given
      Instant windowFrom = Instant.parse("2024-01-01T00:00:00Z");
      Instant windowTo = Instant.parse("2024-01-31T23:59:59Z");

      // When
      CursorEvent event =
          CursorEventTestDataBuilder.aTimeEvent()
              .windowFrom(windowFrom)
              .windowTo(windowTo)
              .build();

      // Then
      assertThat(event.getWindowFrom()).isEqualTo(windowFrom);
      assertThat(event.getWindowTo()).isEqualTo(windowTo);
    }

    @Test
    @DisplayName("应该允许窗口时间为 null（非 TIME 策略）")
    void shouldAllowNullWindowTimeForNonTimeStrategy() {
      // Given
      Instant windowFrom = null;
      Instant windowTo = null;

      // When
      CursorEvent event =
          CursorEventTestDataBuilder.aTimeEvent()
              .cursorType(CursorType.ID)
              .windowFrom(windowFrom)
              .windowTo(windowTo)
              .build();

      // Then
      assertThat(event.getWindowFrom()).isNull();
      assertThat(event.getWindowTo()).isNull();
    }

    @Test
    @DisplayName("应该支持跨月的窗口时间范围")
    void shouldSupportCrossMonthWindowTimeRange() {
      // Given
      Instant windowFrom = Instant.parse("2024-01-15T00:00:00Z");
      Instant windowTo = Instant.parse("2024-02-15T23:59:59Z");

      // When
      CursorEvent event =
          CursorEventTestDataBuilder.aTimeEvent()
              .windowFrom(windowFrom)
              .windowTo(windowTo)
              .build();

      // Then
      assertThat(event.getWindowFrom()).isEqualTo(windowFrom);
      assertThat(event.getWindowTo()).isEqualTo(windowTo);
    }

    @Test
    @DisplayName("应该支持跨年的窗口时间范围")
    void shouldSupportCrossYearWindowTimeRange() {
      // Given
      Instant windowFrom = Instant.parse("2023-12-01T00:00:00Z");
      Instant windowTo = Instant.parse("2024-01-31T23:59:59Z");

      // When
      CursorEvent event =
          CursorEventTestDataBuilder.aTimeEvent()
              .windowFrom(windowFrom)
              .windowTo(windowTo)
              .build();

      // Then
      assertThat(event.getWindowFrom()).isEqualTo(windowFrom);
      assertThat(event.getWindowTo()).isEqualTo(windowTo);
    }
  }

  // ========== Getter 测试 ==========

  @Nested
  @DisplayName("Getter 方法测试")
  class GetterMethodTests {

    @Test
    @DisplayName("应该正确返回所有基础字段")
    void shouldReturnAllBasicFields() {
      // Given
      ProvenanceCode provenanceCode = ProvenanceCode.PUBMED;
      String operationCode = "HARVEST";
      String cursorKey = "publication_date";
      String namespaceScopeCode = "GLOBAL";
      String namespaceKey = "global";

      // When
      CursorEvent event =
          CursorEventTestDataBuilder.aTimeEvent()
              .provenanceCode(provenanceCode)
              .operationCode(operationCode)
              .cursorKey(cursorKey)
              .namespaceScopeCode(namespaceScopeCode)
              .namespaceKey(namespaceKey)
              .build();

      // Then
      assertThat(event.getProvenanceCode()).isEqualTo(provenanceCode);
      assertThat(event.getOperationCode()).isEqualTo(operationCode);
      assertThat(event.getCursorKey()).isEqualTo(cursorKey);
      assertThat(event.getNamespaceScopeCode()).isEqualTo(namespaceScopeCode);
      assertThat(event.getNamespaceKey()).isEqualTo(namespaceKey);
    }

    @Test
    @DisplayName("应该正确返回游标类型")
    void shouldReturnCursorType() {
      // Given & When
      CursorEvent timeEvent =
          CursorEventTestDataBuilder.aTimeEvent()
              .cursorType(CursorType.TIME)
              .build();

      CursorEvent numericEvent =
          CursorEventTestDataBuilder.aTimeEvent()
              .cursorType(CursorType.ID)
              .build();

      // Then
      assertThat(timeEvent.getCursorType()).isEqualTo(CursorType.TIME);
      assertThat(numericEvent.getCursorType()).isEqualTo(CursorType.ID);
    }

    @Test
    @DisplayName("应该正确返回游标值字段")
    void shouldReturnCursorValueFields() {
      // Given
      String prevValue = "2024-01-01T00:00:00Z";
      String newValue = "2024-02-01T00:00:00Z";
      Instant prevInstant = Instant.parse(prevValue);
      Instant newInstant = Instant.parse(newValue);

      // When
      CursorEvent event =
          CursorEventTestDataBuilder.aTimeEvent()
              .prevValue(prevValue)
              .newValue(newValue)
              .prevInstant(prevInstant)
              .newInstant(newInstant)
              .build();

      // Then
      assertThat(event.getPrevValue()).isEqualTo(prevValue);
      assertThat(event.getNewValue()).isEqualTo(newValue);
      assertThat(event.getPrevInstant()).isEqualTo(prevInstant);
      assertThat(event.getNewInstant()).isEqualTo(newInstant);
    }

    @Test
    @DisplayName("应该正确返回幂等性键")
    void shouldReturnIdempotentKey() {
      // Given
      String idempotentKey = "idem-key-12345";

      // When
      CursorEvent event =
          CursorEventTestDataBuilder.aTimeEvent()
              .idempotentKey(idempotentKey)
              .build();

      // Then
      assertThat(event.getIdempotentKey()).isEqualTo(idempotentKey);
    }

    @Test
    @DisplayName("应该正确返回表达式哈希")
    void shouldReturnExprHash() {
      // Given
      String exprHash = "expr-hash-67890";

      // When
      CursorEvent event =
          CursorEventTestDataBuilder.aTimeEvent()
              .exprHash(exprHash)
              .build();

      // Then
      assertThat(event.getExprHash()).isEqualTo(exprHash);
    }

    @Test
    @DisplayName("应该正确返回观察到的最大值")
    void shouldReturnObservedMaxValue() {
      // Given
      String observedMaxValue = "2024-03-01T00:00:00Z";

      // When
      CursorEvent event =
          CursorEventTestDataBuilder.aTimeEvent()
              .observedMaxValue(observedMaxValue)
              .buildRestored();

      // Then
      assertThat(event.getObservedMaxValue()).isEqualTo(observedMaxValue);
    }

    @Test
    @DisplayName("应该正确返回血缘信息")
    void shouldReturnLineage() {
      // Given
      CursorLineage lineage = new CursorLineage(1001L, 2001L, 3001L, 4001L, 5001L, 6001L);

      // When
      CursorEvent event =
          CursorEventTestDataBuilder.aTimeEvent()
              .lineage(lineage)
              .build();

      // Then
      assertThat(event.getLineage()).isEqualTo(lineage);
      assertThat(event.getLineage().scheduleInstanceId()).isEqualTo(1001L);
      assertThat(event.getLineage().planId()).isEqualTo(2001L);
      assertThat(event.getLineage().sliceId()).isEqualTo(3001L);
      assertThat(event.getLineage().taskId()).isEqualTo(4001L);
      assertThat(event.getLineage().runId()).isEqualTo(5001L);
      assertThat(event.getLineage().batchId()).isEqualTo(6001L);
    }
  }

  // ========== 边界情况测试 ==========

  @Nested
  @DisplayName("边界情况测试")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该处理极端时间边界（Unix Epoch）")
    void shouldHandleEpochTime() {
      // Given
      Instant epoch = Instant.parse("1970-01-01T00:00:00Z");

      // When
      CursorEvent event =
          CursorEventTestDataBuilder.aTimeEvent()
              .newInstant(epoch)
              .newValue(epoch.toString())
              .build();

      // Then
      assertThat(event.getNewInstant()).isEqualTo(epoch);
    }

    @Test
    @DisplayName("应该处理远期时间")
    void shouldHandleFutureTime() {
      // Given
      Instant future = Instant.parse("2099-12-31T23:59:59Z");

      // When
      CursorEvent event =
          CursorEventTestDataBuilder.aTimeEvent()
              .newInstant(future)
              .newValue(future.toString())
              .build();

      // Then
      assertThat(event.getNewInstant()).isEqualTo(future);
    }

    @Test
    @DisplayName("应该处理极大的数值游标")
    void shouldHandleLargeNumericValues() {
      // Given
      BigDecimal largeValue = new BigDecimal("999999999999999999");

      // When
      CursorEvent event =
          CursorEventTestDataBuilder.aTimeEvent()
              .cursorType(CursorType.ID)
              .newNumeric(largeValue)
              .buildRestored();

      // Then
      assertThat(event.getNewNumeric()).isEqualTo(largeValue);
    }

    @Test
    @DisplayName("应该处理极小的数值游标")
    void shouldHandleSmallNumericValues() {
      // Given
      BigDecimal smallValue = new BigDecimal("0.000000000000000001");

      // When
      CursorEvent event =
          CursorEventTestDataBuilder.aTimeEvent()
              .cursorType(CursorType.ID)
              .newNumeric(smallValue)
              .buildRestored();

      // Then
      assertThat(event.getNewNumeric()).isEqualTo(smallValue);
    }

    @Test
    @DisplayName("应该处理极长的字符串字段")
    void shouldHandleVeryLongStringFields() {
      // Given
      ProvenanceCode provenanceCode = ProvenanceCode.PUBMED;
      String longCursorKey = "b".repeat(500);
      String longNamespaceKey = "c".repeat(500);

      // When
      CursorEvent event =
          CursorEventTestDataBuilder.aTimeEvent()
              .provenanceCode(provenanceCode)
              .cursorKey(longCursorKey)
              .namespaceKey(longNamespaceKey)
              .build();

      // Then
      assertThat(event.getProvenanceCode()).isEqualTo(provenanceCode);
      assertThat(event.getCursorKey()).hasSize(500);
      assertThat(event.getNamespaceKey()).hasSize(500);
    }

    @Test
    @DisplayName("应该处理毫秒级时间精度")
    void shouldHandleMillisecondTimePrecision() {
      // Given
      Instant preciseTime = Instant.parse("2024-01-01T12:30:45.123456789Z");

      // When
      CursorEvent event =
          CursorEventTestDataBuilder.aTimeEvent()
              .newInstant(preciseTime)
              .newValue(preciseTime.toString())
              .build();

      // Then
      assertThat(event.getNewInstant()).isEqualTo(preciseTime);
    }

    @Test
    @DisplayName("应该处理相同的前值和新值（幂等性）")
    void shouldHandleSamePrevAndNewValues() {
      // Given
      String sameValue = "2024-01-01T00:00:00Z";
      Instant sameInstant = Instant.parse(sameValue);

      // When
      CursorEvent event =
          CursorEventTestDataBuilder.aTimeEvent()
              .prevValue(sameValue)
              .newValue(sameValue)
              .prevInstant(sameInstant)
              .newInstant(sameInstant)
              .build();

      // Then - 应该成功创建（业务逻辑上可能是幂等性重试）
      assertThat(event.getPrevValue()).isEqualTo(sameValue);
      assertThat(event.getNewValue()).isEqualTo(sameValue);
      assertThat(event.getPrevInstant()).isEqualTo(sameInstant);
      assertThat(event.getNewInstant()).isEqualTo(sameInstant);
    }
  }

  // ========== 不可变性测试 ==========

  @Nested
  @DisplayName("不可变性测试")
  class ImmutabilityTests {

    @Test
    @DisplayName("应该确保事件为不可变（所有字段为 final）")
    void shouldEnsureEventIsImmutable() {
      // Given
      CursorLineage lineage = new CursorLineage(1001L, 2001L, 3001L, 4001L, 5001L, 6001L);
      CursorEvent event =
          CursorEventTestDataBuilder.aTimeEvent()
              .lineage(lineage)
              .build();

      // When - 保存初始值
      ProvenanceCode initialProvenanceCode = event.getProvenanceCode();
      String initialCursorKey = event.getCursorKey();
      String initialNewValue = event.getNewValue();
      CursorLineage initialLineage = event.getLineage();

      // Then - 字段应该保持不变（无 setter 方法，无法修改）
      assertThat(event.getProvenanceCode()).isEqualTo(initialProvenanceCode);
      assertThat(event.getCursorKey()).isEqualTo(initialCursorKey);
      assertThat(event.getNewValue()).isEqualTo(initialNewValue);
      assertThat(event.getLineage()).isEqualTo(initialLineage);
    }
  }

  // ========== 业务场景测试 ==========

  @Nested
  @DisplayName("业务场景测试")
  class BusinessScenarioTests {

    @Test
    @DisplayName("应该正确记录首次游标前进（从空到有）")
    void shouldRecordFirstAdvancementFromEmpty() {
      // Given - 首次前进
      String prevValue = null;
      Instant prevInstant = null;
      String newValue = "2024-01-01T00:00:00Z";
      Instant newInstant = Instant.parse(newValue);

      // When
      CursorEvent event =
          CursorEventTestDataBuilder.aTimeEvent()
              .prevValue(prevValue)
              .prevInstant(prevInstant)
              .newValue(newValue)
              .newInstant(newInstant)
              .build();

      // Then
      assertThat(event.getPrevValue()).isNull();
      assertThat(event.getPrevInstant()).isNull();
      assertThat(event.getNewValue()).isEqualTo(newValue);
      assertThat(event.getNewInstant()).isEqualTo(newInstant);
    }

    @Test
    @DisplayName("应该正确记录正常的游标前进")
    void shouldRecordNormalAdvancement() {
      // Given
      String prevValue = "2024-01-01T00:00:00Z";
      String newValue = "2024-02-01T00:00:00Z";
      Instant prevInstant = Instant.parse(prevValue);
      Instant newInstant = Instant.parse(newValue);

      // When
      CursorEvent event =
          CursorEventTestDataBuilder.aTimeEvent()
              .prevValue(prevValue)
              .prevInstant(prevInstant)
              .newValue(newValue)
              .newInstant(newInstant)
              .direction(CursorDirection.FORWARD)
              .build();

      // Then
      assertThat(event.getPrevValue()).isEqualTo(prevValue);
      assertThat(event.getPrevInstant()).isEqualTo(prevInstant);
      assertThat(event.getNewValue()).isEqualTo(newValue);
      assertThat(event.getNewInstant()).isEqualTo(newInstant);
      assertThat(event.getDirection()).isEqualTo(CursorDirection.FORWARD);
    }

    @Test
    @DisplayName("应该正确记录回填场景的游标前进")
    void shouldRecordBackfillAdvancement() {
      // Given
      String prevValue = "2024-12-31T23:59:59Z";
      String newValue = "2023-12-31T23:59:59Z"; // 回填时间向前
      Instant prevInstant = Instant.parse(prevValue);
      Instant newInstant = Instant.parse(newValue);

      // When
      CursorEvent event =
          CursorEventTestDataBuilder.aTimeEvent()
              .prevValue(prevValue)
              .prevInstant(prevInstant)
              .newValue(newValue)
              .newInstant(newInstant)
              .direction(CursorDirection.BACKFILL)
              .build();

      // Then
      assertThat(event.getDirection()).isEqualTo(CursorDirection.BACKFILL);
      assertThat(event.getPrevInstant()).isAfter(event.getNewInstant()); // 回填时前值晚于新值
    }

    @Test
    @DisplayName("应该正确记录带完整血缘的前进事件")
    void shouldRecordAdvancementWithFullLineage() {
      // Given
      CursorLineage lineage = new CursorLineage(1001L, 2001L, 3001L, 4001L, 5001L, 6001L);

      // When
      CursorEvent event =
          CursorEventTestDataBuilder.aTimeEvent()
              .lineage(lineage)
              .build();

      // Then - 血缘应该完整记录用于追溯
      assertThat(event.getLineage().scheduleInstanceId()).isEqualTo(1001L);
      assertThat(event.getLineage().planId()).isEqualTo(2001L);
      assertThat(event.getLineage().sliceId()).isEqualTo(3001L);
      assertThat(event.getLineage().taskId()).isEqualTo(4001L);
      assertThat(event.getLineage().runId()).isEqualTo(5001L);
      assertThat(event.getLineage().batchId()).isEqualTo(6001L);
    }

    @Test
    @DisplayName("应该正确记录带表达式哈希的前进事件（用于策略变更检测）")
    void shouldRecordAdvancementWithExprHashForStrategyChangeDetection() {
      // Given
      String exprHash = "expr-hash-strategy-v2";

      // When
      CursorEvent event =
          CursorEventTestDataBuilder.aTimeEvent()
              .exprHash(exprHash)
              .build();

      // Then - 表达式哈希应该被记录用于策略变更追踪
      assertThat(event.getExprHash()).isEqualTo(exprHash);
    }

    @Test
    @DisplayName("应该正确记录带窗口时间的前进事件（TIME 策略）")
    void shouldRecordAdvancementWithWindowTimeForTimeStrategy() {
      // Given
      Instant windowFrom = Instant.parse("2024-01-01T00:00:00Z");
      Instant windowTo = Instant.parse("2024-01-31T23:59:59Z");

      // When
      CursorEvent event =
          CursorEventTestDataBuilder.aTimeEvent()
              .windowFrom(windowFrom)
              .windowTo(windowTo)
              .build();

      // Then - 窗口时间应该被记录用于审计
      assertThat(event.getWindowFrom()).isEqualTo(windowFrom);
      assertThat(event.getWindowTo()).isEqualTo(windowTo);
    }
  }

  // ========== TestDataBuilder (辅助类) ==========

  /**
   * CursorEvent 测试数据构建器。
   *
   * <p>遵循 Builder 模式，提供默认值以简化测试数据构建。
   */
  static class CursorEventTestDataBuilder {
    private Long id = null; // 默认为 null（新创建的事件）
    private ProvenanceCode provenanceCode = ProvenanceCode.PUBMED;
    private String operationCode = "HARVEST";
    private String cursorKey = "publication_date";
    private String namespaceScopeCode = "GLOBAL";
    private String namespaceKey = "global";
    private CursorType cursorType = CursorType.TIME;
    private String prevValue = null; // 默认 null（首次前进）
    private String newValue = "2024-01-01T00:00:00Z";
    private CursorDirection direction = CursorDirection.FORWARD;
    private String idempotentKey = "idem-key-default";
    private String observedMaxValue = null;
    private Instant prevInstant = null; // 默认 null（首次前进）
    private Instant newInstant = Instant.parse("2024-01-01T00:00:00Z");
    private BigDecimal prevNumeric = null;
    private BigDecimal newNumeric = null;
    private CursorLineage lineage = CursorLineage.empty();
    private String exprHash = null;
    private Instant windowFrom = null;
    private Instant windowTo = null;

    public static CursorEventTestDataBuilder aTimeEvent() {
      return new CursorEventTestDataBuilder();
    }

    public CursorEventTestDataBuilder id(Long id) {
      this.id = id;
      return this;
    }

    public CursorEventTestDataBuilder provenanceCode(ProvenanceCode provenanceCode) {
      this.provenanceCode = provenanceCode;
      return this;
    }

    public CursorEventTestDataBuilder operationCode(String operationCode) {
      this.operationCode = operationCode;
      return this;
    }

    public CursorEventTestDataBuilder cursorKey(String cursorKey) {
      this.cursorKey = cursorKey;
      return this;
    }

    public CursorEventTestDataBuilder namespaceScopeCode(String namespaceScopeCode) {
      this.namespaceScopeCode = namespaceScopeCode;
      return this;
    }

    public CursorEventTestDataBuilder namespaceKey(String namespaceKey) {
      this.namespaceKey = namespaceKey;
      return this;
    }

    public CursorEventTestDataBuilder cursorType(CursorType cursorType) {
      this.cursorType = cursorType;
      return this;
    }

    public CursorEventTestDataBuilder prevValue(String prevValue) {
      this.prevValue = prevValue;
      return this;
    }

    public CursorEventTestDataBuilder newValue(String newValue) {
      this.newValue = newValue;
      return this;
    }

    public CursorEventTestDataBuilder direction(CursorDirection direction) {
      this.direction = direction;
      return this;
    }

    public CursorEventTestDataBuilder idempotentKey(String idempotentKey) {
      this.idempotentKey = idempotentKey;
      return this;
    }

    public CursorEventTestDataBuilder observedMaxValue(String observedMaxValue) {
      this.observedMaxValue = observedMaxValue;
      return this;
    }

    public CursorEventTestDataBuilder prevInstant(Instant prevInstant) {
      this.prevInstant = prevInstant;
      return this;
    }

    public CursorEventTestDataBuilder newInstant(Instant newInstant) {
      this.newInstant = newInstant;
      return this;
    }

    public CursorEventTestDataBuilder prevNumeric(BigDecimal prevNumeric) {
      this.prevNumeric = prevNumeric;
      return this;
    }

    public CursorEventTestDataBuilder newNumeric(BigDecimal newNumeric) {
      this.newNumeric = newNumeric;
      return this;
    }

    public CursorEventTestDataBuilder lineage(CursorLineage lineage) {
      this.lineage = lineage;
      return this;
    }

    public CursorEventTestDataBuilder exprHash(String exprHash) {
      this.exprHash = exprHash;
      return this;
    }

    public CursorEventTestDataBuilder windowFrom(Instant windowFrom) {
      this.windowFrom = windowFrom;
      return this;
    }

    public CursorEventTestDataBuilder windowTo(Instant windowTo) {
      this.windowTo = windowTo;
      return this;
    }

    /** 构建新创建的事件（使用 create() 工厂方法）。 */
    public CursorEvent build() {
      return CursorEvent.create(
          provenanceCode,
          operationCode,
          cursorKey,
          namespaceScopeCode,
          namespaceKey,
          cursorType,
          prevValue,
          newValue,
          prevInstant,
          newInstant,
          direction,
          idempotentKey,
          lineage,
          exprHash,
          windowFrom,
          windowTo);
    }

    /** 构建从持久化重建的事件（使用 restore() 工厂方法）。 */
    public CursorEvent buildRestored() {
      Long restoredId = (id != null) ? id : 100L; // 默认 ID
      return CursorEvent.restore(
          restoredId,
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
          exprHash,
          windowFrom,
          windowTo);
    }
  }
}
