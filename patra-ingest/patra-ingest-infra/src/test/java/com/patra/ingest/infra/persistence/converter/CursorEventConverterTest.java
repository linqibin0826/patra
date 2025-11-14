package com.patra.ingest.infra.persistence.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.entity.CursorEvent;
import com.patra.ingest.domain.model.enums.CursorDirection;
import com.patra.ingest.domain.model.enums.CursorType;
import com.patra.ingest.domain.model.vo.cursor.CursorLineage;
import com.patra.ingest.infra.persistence.entity.CursorEventDO;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * CursorEventConverter 单元测试。
 *
 * <p>测试 Domain 实体与 DO 之间的双向转换,包括:
 *
 * <ul>
 *   <li>枚举转换(CursorType、CursorDirection ↔ String)
 *   <li>CursorLineage 展开和聚合
 *   <li>所有枚举值的转换
 *   <li>Null 值处理
 * </ul>
 *
 * @author Patra Team
 * @since 0.1.0
 */
@DisplayName("CursorEventConverter 单元测试")
class CursorEventConverterTest {

  private final CursorEventConverter converter = new CursorEventConverterImpl();

  // 测试常量
  private static final ProvenanceCode PROVENANCE_CODE = ProvenanceCode.PUBMED;
  private static final String OPERATION_CODE = "HARVEST";
  private static final String CURSOR_KEY = "search:cancer";
  private static final String NAMESPACE_SCOPE_CODE = "GLOBAL";
  private static final String NAMESPACE_KEY = "default";
  private static final String PREV_VALUE = "2025-01-01T00:00:00Z";
  private static final String NEW_VALUE = "2025-01-15T00:00:00Z";
  private static final String OBSERVED_MAX_VALUE = "2025-01-15T23:59:59Z";
  private static final String IDEMPOTENT_KEY = "PUBMED:HARVEST:search:cancer:2025-01-15";
  private static final Instant PREV_INSTANT = Instant.parse("2025-01-01T00:00:00Z");
  private static final Instant NEW_INSTANT = Instant.parse("2025-01-15T00:00:00Z");
  private static final BigDecimal PREV_NUMERIC = new BigDecimal("1000");
  private static final BigDecimal NEW_NUMERIC = new BigDecimal("2000");
  private static final String EXPR_HASH = "hash-abc123";
  private static final Instant WINDOW_FROM = Instant.parse("2025-01-01T00:00:00Z");
  private static final Instant WINDOW_TO = Instant.parse("2025-01-15T00:00:00Z");

  @Nested
  @DisplayName("toDO() 转换测试")
  class ToDOConversionTests {

    @Test
    @DisplayName("应该正确转换 TIME 类型 FORWARD 方向的游标事件")
    void shouldConvertTimeForwardEventToDO() {
      // Given: 创建 TIME 类型 FORWARD 方向的领域实体
      CursorLineage lineage = new CursorLineage(1L, 2L, 3L, 4L, 5L, 6L);
      CursorEvent event =
          CursorEvent.restore(
              100L,
              PROVENANCE_CODE,
              OPERATION_CODE,
              CURSOR_KEY,
              NAMESPACE_SCOPE_CODE,
              NAMESPACE_KEY,
              CursorType.TIME,
              PREV_VALUE,
              NEW_VALUE,
              CursorDirection.FORWARD,
              IDEMPOTENT_KEY,
              OBSERVED_MAX_VALUE,
              PREV_INSTANT,
              NEW_INSTANT,
              null, // TIME 类型无数值
              null,
              lineage,
              EXPR_HASH,
              WINDOW_FROM,
              WINDOW_TO);

      // When: 转换为 DO
      CursorEventDO result = converter.toDO(event);

      // Then: 验证转换结果
      assertThat(result).isNotNull();
      assertThat(result.getProvenanceCode()).isEqualTo(PROVENANCE_CODE.getCode());
      assertThat(result.getOperationCode()).isEqualTo(OPERATION_CODE);
      assertThat(result.getCursorKey()).isEqualTo(CURSOR_KEY);
      assertThat(result.getNamespaceScopeCode()).isEqualTo(NAMESPACE_SCOPE_CODE);
      assertThat(result.getNamespaceKey()).isEqualTo(NAMESPACE_KEY);
      assertThat(result.getCursorTypeCode()).isEqualTo("TIME");
      assertThat(result.getPrevValue()).isEqualTo(PREV_VALUE);
      assertThat(result.getNewValue()).isEqualTo(NEW_VALUE);
      assertThat(result.getDirectionCode()).isEqualTo("FORWARD");
      assertThat(result.getIdempotentKey()).isEqualTo(IDEMPOTENT_KEY);
      assertThat(result.getObservedMaxValue()).isEqualTo(OBSERVED_MAX_VALUE);
      assertThat(result.getPrevInstant()).isEqualTo(PREV_INSTANT);
      assertThat(result.getNewInstant()).isEqualTo(NEW_INSTANT);
      assertThat(result.getPrevNumeric()).isNull();
      assertThat(result.getNewNumeric()).isNull();
      // 验证 CursorLineage 展开
      assertThat(result.getScheduleInstanceId()).isEqualTo(1L);
      assertThat(result.getPlanId()).isEqualTo(2L);
      assertThat(result.getSliceId()).isEqualTo(3L);
      assertThat(result.getTaskId()).isEqualTo(4L);
      assertThat(result.getRunId()).isEqualTo(5L);
      assertThat(result.getBatchId()).isEqualTo(6L);
      assertThat(result.getExprHash()).isEqualTo(EXPR_HASH);
      assertThat(result.getWindowFrom()).isEqualTo(WINDOW_FROM);
      assertThat(result.getWindowTo()).isEqualTo(WINDOW_TO);
    }

    @Test
    @DisplayName("应该正确转换 ID 类型 BACKFILL 方向的游标事件")
    void shouldConvertIdBackfillEventToDO() {
      // Given: 创建 ID 类型 BACKFILL 方向的领域实体
      CursorLineage lineage = new CursorLineage(10L, 20L, 30L, 40L, 50L, 60L);
      CursorEvent event =
          CursorEvent.restore(
              200L,
              PROVENANCE_CODE,
              OPERATION_CODE,
              CURSOR_KEY,
              NAMESPACE_SCOPE_CODE,
              NAMESPACE_KEY,
              CursorType.ID,
              PREV_VALUE,
              NEW_VALUE,
              CursorDirection.BACKFILL,
              IDEMPOTENT_KEY,
              OBSERVED_MAX_VALUE,
              null, // ID 类型无时间
              null,
              PREV_NUMERIC,
              NEW_NUMERIC,
              lineage,
              EXPR_HASH,
              null, // ID 类型无窗口
              null);

      // When: 转换为 DO
      CursorEventDO result = converter.toDO(event);

      // Then: 验证转换结果
      assertThat(result).isNotNull();
      assertThat(result.getCursorTypeCode()).isEqualTo("ID");
      assertThat(result.getDirectionCode()).isEqualTo("BACKFILL");
      assertThat(result.getPrevInstant()).isNull();
      assertThat(result.getNewInstant()).isNull();
      assertThat(result.getPrevNumeric()).isEqualTo(PREV_NUMERIC);
      assertThat(result.getNewNumeric()).isEqualTo(NEW_NUMERIC);
      assertThat(result.getWindowFrom()).isNull();
      assertThat(result.getWindowTo()).isNull();
      // 验证 CursorLineage 展开
      assertThat(result.getScheduleInstanceId()).isEqualTo(10L);
      assertThat(result.getPlanId()).isEqualTo(20L);
      assertThat(result.getSliceId()).isEqualTo(30L);
      assertThat(result.getTaskId()).isEqualTo(40L);
      assertThat(result.getRunId()).isEqualTo(50L);
      assertThat(result.getBatchId()).isEqualTo(60L);
    }

    @Test
    @DisplayName("应该正确转换 TOKEN 类型的游标事件")
    void shouldConvertTokenEventToDO() {
      // Given: 创建 TOKEN 类型的领域实体
      CursorLineage lineage = new CursorLineage(7L, 8L, 9L, 10L, 11L, 12L);
      CursorEvent event =
          CursorEvent.restore(
              300L,
              PROVENANCE_CODE,
              OPERATION_CODE,
              CURSOR_KEY,
              NAMESPACE_SCOPE_CODE,
              NAMESPACE_KEY,
              CursorType.TOKEN,
              "token-prev",
              "token-new",
              CursorDirection.FORWARD,
              IDEMPOTENT_KEY,
              "token-max",
              null,
              null,
              null,
              null,
              lineage,
              EXPR_HASH,
              null,
              null);

      // When: 转换为 DO
      CursorEventDO result = converter.toDO(event);

      // Then: 验证转换结果
      assertThat(result).isNotNull();
      assertThat(result.getCursorTypeCode()).isEqualTo("TOKEN");
      assertThat(result.getPrevValue()).isEqualTo("token-prev");
      assertThat(result.getNewValue()).isEqualTo("token-new");
      assertThat(result.getObservedMaxValue()).isEqualTo("token-max");
    }

    @Test
    @DisplayName("应该正确处理 null 的可选字段")
    void shouldHandleNullOptionalFields() {
      // Given: 创建包含 null 可选字段的领域实体
      CursorLineage lineage = new CursorLineage(null, null, null, null, null, null);
      CursorEvent event =
          CursorEvent.restore(
              100L,
              PROVENANCE_CODE,
              OPERATION_CODE,
              CURSOR_KEY,
              NAMESPACE_SCOPE_CODE,
              NAMESPACE_KEY,
              CursorType.TIME,
              null, // 第一次推进,prevValue 为 null
              NEW_VALUE,
              CursorDirection.FORWARD,
              IDEMPOTENT_KEY,
              null, // 可选字段
              null,
              NEW_INSTANT,
              null,
              null,
              lineage,
              null, // 可选字段
              WINDOW_FROM,
              WINDOW_TO);

      // When: 转换为 DO
      CursorEventDO result = converter.toDO(event);

      // Then: 验证 null 值正确传递
      assertThat(result).isNotNull();
      assertThat(result.getPrevValue()).isNull();
      assertThat(result.getObservedMaxValue()).isNull();
      assertThat(result.getPrevInstant()).isNull();
      assertThat(result.getExprHash()).isNull();
      assertThat(result.getScheduleInstanceId()).isNull();
      assertThat(result.getPlanId()).isNull();
      assertThat(result.getSliceId()).isNull();
      assertThat(result.getTaskId()).isNull();
      assertThat(result.getRunId()).isNull();
      assertThat(result.getBatchId()).isNull();
    }
  }

  @Nested
  @DisplayName("toDomain() 转换测试")
  class ToDomainConversionTests {

    @Test
    @DisplayName("应该正确转换 TIME 类型 FORWARD 方向的 DO")
    void shouldConvertTimeForwardDOToDomain() {
      // Given: 创建 TIME 类型 FORWARD 方向的 DO
      CursorEventDO entity = new CursorEventDO();
      entity.setId(100L);
      entity.setProvenanceCode(PROVENANCE_CODE.getCode());
      entity.setOperationCode(OPERATION_CODE);
      entity.setCursorKey(CURSOR_KEY);
      entity.setNamespaceScopeCode(NAMESPACE_SCOPE_CODE);
      entity.setNamespaceKey(NAMESPACE_KEY);
      entity.setCursorTypeCode("TIME");
      entity.setPrevValue(PREV_VALUE);
      entity.setNewValue(NEW_VALUE);
      entity.setDirectionCode("FORWARD");
      entity.setIdempotentKey(IDEMPOTENT_KEY);
      entity.setObservedMaxValue(OBSERVED_MAX_VALUE);
      entity.setPrevInstant(PREV_INSTANT);
      entity.setNewInstant(NEW_INSTANT);
      entity.setScheduleInstanceId(1L);
      entity.setPlanId(2L);
      entity.setSliceId(3L);
      entity.setTaskId(4L);
      entity.setRunId(5L);
      entity.setBatchId(6L);
      entity.setExprHash(EXPR_HASH);
      entity.setWindowFrom(WINDOW_FROM);
      entity.setWindowTo(WINDOW_TO);

      // When: 转换为领域实体
      CursorEvent result = converter.toDomain(entity);

      // Then: 验证转换结果
      assertThat(result).isNotNull();
      assertThat(result.getId()).isEqualTo(100L);
      assertThat(result.getProvenanceCode()).isEqualTo(PROVENANCE_CODE);
      assertThat(result.getOperationCode()).isEqualTo(OPERATION_CODE);
      assertThat(result.getCursorKey()).isEqualTo(CURSOR_KEY);
      assertThat(result.getNamespaceScopeCode()).isEqualTo(NAMESPACE_SCOPE_CODE);
      assertThat(result.getNamespaceKey()).isEqualTo(NAMESPACE_KEY);
      assertThat(result.getCursorType()).isEqualTo(CursorType.TIME);
      assertThat(result.getPrevValue()).isEqualTo(PREV_VALUE);
      assertThat(result.getNewValue()).isEqualTo(NEW_VALUE);
      assertThat(result.getDirection()).isEqualTo(CursorDirection.FORWARD);
      assertThat(result.getIdempotentKey()).isEqualTo(IDEMPOTENT_KEY);
      assertThat(result.getObservedMaxValue()).isEqualTo(OBSERVED_MAX_VALUE);
      assertThat(result.getPrevInstant()).isEqualTo(PREV_INSTANT);
      assertThat(result.getNewInstant()).isEqualTo(NEW_INSTANT);
      // 验证 CursorLineage 聚合
      assertThat(result.getLineage()).isNotNull();
      assertThat(result.getLineage().scheduleInstanceId()).isEqualTo(1L);
      assertThat(result.getLineage().planId()).isEqualTo(2L);
      assertThat(result.getLineage().sliceId()).isEqualTo(3L);
      assertThat(result.getLineage().taskId()).isEqualTo(4L);
      assertThat(result.getLineage().runId()).isEqualTo(5L);
      assertThat(result.getLineage().batchId()).isEqualTo(6L);
      assertThat(result.getExprHash()).isEqualTo(EXPR_HASH);
      assertThat(result.getWindowFrom()).isEqualTo(WINDOW_FROM);
      assertThat(result.getWindowTo()).isEqualTo(WINDOW_TO);
    }

    @Test
    @DisplayName("应该正确转换 ID 类型 BACKFILL 方向的 DO")
    void shouldConvertIdBackfillDOToDomain() {
      // Given: 创建 ID 类型 BACKFILL 方向的 DO
      CursorEventDO entity = new CursorEventDO();
      entity.setId(200L);
      entity.setProvenanceCode(PROVENANCE_CODE.getCode());
      entity.setOperationCode(OPERATION_CODE);
      entity.setCursorKey(CURSOR_KEY);
      entity.setNamespaceScopeCode(NAMESPACE_SCOPE_CODE);
      entity.setNamespaceKey(NAMESPACE_KEY);
      entity.setCursorTypeCode("ID");
      entity.setPrevValue(PREV_VALUE);
      entity.setNewValue(NEW_VALUE);
      entity.setDirectionCode("BACKFILL");
      entity.setIdempotentKey(IDEMPOTENT_KEY);
      entity.setPrevNumeric(PREV_NUMERIC);
      entity.setNewNumeric(NEW_NUMERIC);
      entity.setScheduleInstanceId(10L);
      entity.setPlanId(20L);
      entity.setSliceId(30L);
      entity.setTaskId(40L);
      entity.setRunId(50L);
      entity.setBatchId(60L);
      entity.setExprHash(EXPR_HASH);

      // When: 转换为领域实体
      CursorEvent result = converter.toDomain(entity);

      // Then: 验证转换结果
      assertThat(result).isNotNull();
      assertThat(result.getCursorType()).isEqualTo(CursorType.ID);
      assertThat(result.getDirection()).isEqualTo(CursorDirection.BACKFILL);
      assertThat(result.getPrevNumeric()).isEqualTo(PREV_NUMERIC);
      assertThat(result.getNewNumeric()).isEqualTo(NEW_NUMERIC);
      assertThat(result.getLineage().scheduleInstanceId()).isEqualTo(10L);
      assertThat(result.getLineage().planId()).isEqualTo(20L);
      assertThat(result.getLineage().sliceId()).isEqualTo(30L);
      assertThat(result.getLineage().taskId()).isEqualTo(40L);
      assertThat(result.getLineage().runId()).isEqualTo(50L);
      assertThat(result.getLineage().batchId()).isEqualTo(60L);
    }

    @Test
    @DisplayName("应该正确转换 TOKEN 类型的 DO")
    void shouldConvertTokenDOToDomain() {
      // Given: 创建 TOKEN 类型的 DO
      CursorEventDO entity = new CursorEventDO();
      entity.setId(300L);
      entity.setProvenanceCode(PROVENANCE_CODE.getCode());
      entity.setOperationCode(OPERATION_CODE);
      entity.setCursorKey(CURSOR_KEY);
      entity.setNamespaceScopeCode(NAMESPACE_SCOPE_CODE);
      entity.setNamespaceKey(NAMESPACE_KEY);
      entity.setCursorTypeCode("TOKEN");
      entity.setPrevValue("token-prev");
      entity.setNewValue("token-new");
      entity.setDirectionCode("FORWARD");
      entity.setIdempotentKey(IDEMPOTENT_KEY);
      entity.setObservedMaxValue("token-max");
      entity.setScheduleInstanceId(7L);
      entity.setPlanId(8L);
      entity.setSliceId(9L);
      entity.setTaskId(10L);
      entity.setRunId(11L);
      entity.setBatchId(12L);
      entity.setExprHash(EXPR_HASH);

      // When: 转换为领域实体
      CursorEvent result = converter.toDomain(entity);

      // Then: 验证转换结果
      assertThat(result).isNotNull();
      assertThat(result.getCursorType()).isEqualTo(CursorType.TOKEN);
      assertThat(result.getPrevValue()).isEqualTo("token-prev");
      assertThat(result.getNewValue()).isEqualTo("token-new");
      assertThat(result.getObservedMaxValue()).isEqualTo("token-max");
    }

    @Test
    @DisplayName("应该正确处理 null 的 CursorLineage 字段")
    void shouldHandleNullLineageFields() {
      // Given: 创建 CursorLineage 字段全为 null 的 DO
      CursorEventDO entity = new CursorEventDO();
      entity.setId(100L);
      entity.setProvenanceCode(PROVENANCE_CODE.getCode());
      entity.setOperationCode(OPERATION_CODE);
      entity.setCursorKey(CURSOR_KEY);
      entity.setNamespaceScopeCode(NAMESPACE_SCOPE_CODE);
      entity.setNamespaceKey(NAMESPACE_KEY);
      entity.setCursorTypeCode("TIME");
      entity.setNewValue(NEW_VALUE);
      entity.setDirectionCode("FORWARD");
      entity.setIdempotentKey(IDEMPOTENT_KEY);
      entity.setNewInstant(NEW_INSTANT);
      entity.setWindowFrom(WINDOW_FROM);
      entity.setWindowTo(WINDOW_TO);
      // CursorLineage 字段全为 null
      entity.setScheduleInstanceId(null);
      entity.setPlanId(null);
      entity.setSliceId(null);
      entity.setTaskId(null);
      entity.setRunId(null);
      entity.setBatchId(null);

      // When: 转换为领域实体
      CursorEvent result = converter.toDomain(entity);

      // Then: 验证 CursorLineage 包含 null 值
      assertThat(result).isNotNull();
      assertThat(result.getLineage()).isNotNull();
      assertThat(result.getLineage().scheduleInstanceId()).isNull();
      assertThat(result.getLineage().planId()).isNull();
      assertThat(result.getLineage().sliceId()).isNull();
      assertThat(result.getLineage().taskId()).isNull();
      assertThat(result.getLineage().runId()).isNull();
      assertThat(result.getLineage().batchId()).isNull();
    }
  }

  @Nested
  @DisplayName("双向转换一致性测试")
  class RoundTripConsistencyTests {

    @Test
    @DisplayName("应该保证双向转换的一致性")
    void shouldMaintainConsistencyInRoundTripConversion() {
      // Given: 创建完整的领域实体
      CursorLineage lineage = new CursorLineage(1L, 2L, 3L, 4L, 5L, 6L);
      CursorEvent original =
          CursorEvent.restore(
              100L,
              PROVENANCE_CODE,
              OPERATION_CODE,
              CURSOR_KEY,
              NAMESPACE_SCOPE_CODE,
              NAMESPACE_KEY,
              CursorType.TIME,
              PREV_VALUE,
              NEW_VALUE,
              CursorDirection.FORWARD,
              IDEMPOTENT_KEY,
              OBSERVED_MAX_VALUE,
              PREV_INSTANT,
              NEW_INSTANT,
              PREV_NUMERIC,
              NEW_NUMERIC,
              lineage,
              EXPR_HASH,
              WINDOW_FROM,
              WINDOW_TO);

      // When: 双向转换(Domain → DO → Domain)
      CursorEventDO entity = converter.toDO(original);
      // 模拟数据库返回(设置 id)
      entity.setId(100L);
      // 转换为 DO 后，provenanceCode 被转为字符串，所以需要重新设置为枚举值
      entity.setProvenanceCode(PROVENANCE_CODE.getCode());
      CursorEvent result = converter.toDomain(entity);

      // Then: 验证转换后的值与原始值一致
      assertThat(result.getId()).isEqualTo(original.getId());
      assertThat(result.getProvenanceCode()).isEqualTo(original.getProvenanceCode());
      assertThat(result.getOperationCode()).isEqualTo(original.getOperationCode());
      assertThat(result.getCursorKey()).isEqualTo(original.getCursorKey());
      assertThat(result.getNamespaceScopeCode()).isEqualTo(original.getNamespaceScopeCode());
      assertThat(result.getNamespaceKey()).isEqualTo(original.getNamespaceKey());
      assertThat(result.getCursorType()).isEqualTo(original.getCursorType());
      assertThat(result.getPrevValue()).isEqualTo(original.getPrevValue());
      assertThat(result.getNewValue()).isEqualTo(original.getNewValue());
      assertThat(result.getDirection()).isEqualTo(original.getDirection());
      assertThat(result.getIdempotentKey()).isEqualTo(original.getIdempotentKey());
      assertThat(result.getObservedMaxValue()).isEqualTo(original.getObservedMaxValue());
      assertThat(result.getPrevInstant()).isEqualTo(original.getPrevInstant());
      assertThat(result.getNewInstant()).isEqualTo(original.getNewInstant());
      assertThat(result.getPrevNumeric()).isEqualTo(original.getPrevNumeric());
      assertThat(result.getNewNumeric()).isEqualTo(original.getNewNumeric());
      assertThat(result.getLineage().scheduleInstanceId())
          .isEqualTo(original.getLineage().scheduleInstanceId());
      assertThat(result.getLineage().planId()).isEqualTo(original.getLineage().planId());
      assertThat(result.getLineage().sliceId()).isEqualTo(original.getLineage().sliceId());
      assertThat(result.getLineage().taskId()).isEqualTo(original.getLineage().taskId());
      assertThat(result.getLineage().runId()).isEqualTo(original.getLineage().runId());
      assertThat(result.getLineage().batchId()).isEqualTo(original.getLineage().batchId());
      assertThat(result.getExprHash()).isEqualTo(original.getExprHash());
      assertThat(result.getWindowFrom()).isEqualTo(original.getWindowFrom());
      assertThat(result.getWindowTo()).isEqualTo(original.getWindowTo());
    }
  }
}
