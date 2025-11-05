package com.patra.ingest.infra.persistence.converter;

import static org.assertj.core.api.Assertions.*;

import com.patra.ingest.domain.model.entity.Cursor;
import com.patra.ingest.domain.model.enums.CursorType;
import com.patra.ingest.domain.model.enums.NamespaceScope;
import com.patra.ingest.domain.model.vo.cursor.CursorLineage;
import com.patra.ingest.domain.model.vo.cursor.CursorValue;
import com.patra.ingest.domain.model.vo.cursor.CursorWatermark;
import com.patra.ingest.infra.persistence.entity.CursorDO;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * CursorConverter 单元测试。
 *
 * <p>测试策略：
 *
 * <ul>
 *   <li>测试 toDO() 转换的正确性
 *   <li>测试 toDomain() 转换的正确性
 *   <li>测试静态辅助方法
 *   <li>测试 null 安全性
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@DisplayName("CursorConverter 单元测试")
class CursorConverterTest {

  private final CursorConverter converter = new CursorConverterImpl();

  private static final String PROVENANCE_CODE = "PUBMED";
  private static final String OPERATION_CODE = "FETCH_METADATA";
  private static final String CURSOR_KEY = "updated_at";
  private static final String NAMESPACE_KEY = "pubmed:global";
  private static final String EXPR_HASH = "test-expr-hash";

  // ========== toDO() 转换测试 ==========

  @Nested
  @DisplayName("toDO() 转换测试")
  class ToDOConversionTests {

    @Test
    @DisplayName("应该正确转换 TIME 类型游标")
    void shouldConvertTimeTypeCursorToDO() {
      // Given: 创建 TIME 类型游标
      Instant instant = Instant.parse("2025-01-15T10:30:00Z");
      CursorValue value = CursorValue.time(instant);
      CursorWatermark watermark = new CursorWatermark(instant.toString(), instant, null);
      CursorLineage lineage = new CursorLineage(1L, 2L, 3L, 4L, 5L, 6L);

      Cursor cursor =
          Cursor.restore(
              100L,
              PROVENANCE_CODE,
              OPERATION_CODE,
              CURSOR_KEY,
              NamespaceScope.GLOBAL,
              NAMESPACE_KEY,
              CursorType.TIME,
              value,
              watermark,
              lineage,
              EXPR_HASH);

      // When: 转换为 DO
      CursorDO result = converter.toDO(cursor);

      // Then: 验证转换结果
      assertThat(result).isNotNull();
      assertThat(result.getProvenanceCode()).isEqualTo(PROVENANCE_CODE);
      assertThat(result.getOperationCode()).isEqualTo(OPERATION_CODE);
      assertThat(result.getCursorKey()).isEqualTo(CURSOR_KEY);
      assertThat(result.getCursorTypeCode()).isEqualTo("TIME");
      assertThat(result.getNamespaceScopeCode()).isEqualTo("GLOBAL");
      assertThat(result.getNamespaceKey()).isEqualTo(NAMESPACE_KEY);
      assertThat(result.getCursorValue()).isEqualTo(instant.toString());
      assertThat(result.getNormalizedInstant()).isEqualTo(instant);
      assertThat(result.getObservedMaxValue()).isEqualTo(instant.toString());
      assertThat(result.getExprHash()).isEqualTo(EXPR_HASH);

      // 验证 lineage 字段
      assertThat(result.getScheduleInstanceId()).isEqualTo(1L);
      assertThat(result.getPlanId()).isEqualTo(2L);
      assertThat(result.getSliceId()).isEqualTo(3L);
      assertThat(result.getTaskId()).isEqualTo(4L);
      assertThat(result.getLastRunId()).isEqualTo(5L);
      assertThat(result.getLastBatchId()).isEqualTo(6L);
    }

    @Test
    @DisplayName("应该正确转换 ID 类型游标")
    void shouldConvertIdTypeCursorToDO() {
      // Given: 创建 ID 类型游标
      BigDecimal numeric = BigDecimal.valueOf(12345);
      CursorValue value = CursorValue.id(numeric);
      CursorWatermark watermark = new CursorWatermark("12345", null, numeric);
      CursorLineage lineage = CursorLineage.empty();

      Cursor cursor =
          Cursor.restore(
              200L,
              PROVENANCE_CODE,
              OPERATION_CODE,
              "record_id",
              NamespaceScope.EXPR,
              NAMESPACE_KEY,
              CursorType.ID,
              value,
              watermark,
              lineage,
              EXPR_HASH);

      // When: 转换为 DO
      CursorDO result = converter.toDO(cursor);

      // Then: 验证 ID 类型字段
      assertThat(result.getCursorTypeCode()).isEqualTo("ID");
      assertThat(result.getCursorValue()).isEqualTo("12345");
      assertThat(result.getNormalizedNumeric()).isEqualByComparingTo(numeric);
      assertThat(result.getNormalizedInstant()).isNull();
      assertThat(result.getNamespaceScopeCode()).isEqualTo("EXPR");
    }

    @Test
    @DisplayName("应该正确转换 TOKEN 类型游标")
    void shouldConvertTokenTypeCursorToDO() {
      // Given: 创建 TOKEN 类型游标
      String tokenValue = "opaque-cursor-token-xyz";
      CursorValue value = CursorValue.token(tokenValue);
      CursorWatermark watermark = new CursorWatermark("token-max", null, null);
      CursorLineage lineage = CursorLineage.empty();

      Cursor cursor =
          Cursor.restore(
              300L,
              PROVENANCE_CODE,
              OPERATION_CODE,
              "cursor_token",
              NamespaceScope.CUSTOM,
              NAMESPACE_KEY,
              CursorType.TOKEN,
              value,
              watermark,
              lineage,
              EXPR_HASH);

      // When: 转换为 DO
      CursorDO result = converter.toDO(cursor);

      // Then: 验证 TOKEN 类型字段
      assertThat(result.getCursorTypeCode()).isEqualTo("TOKEN");
      assertThat(result.getCursorValue()).isEqualTo(tokenValue);
      assertThat(result.getNormalizedInstant()).isNull();
      assertThat(result.getNormalizedNumeric()).isNull();
      assertThat(result.getObservedMaxValue()).isEqualTo("token-max");
      assertThat(result.getNamespaceScopeCode()).isEqualTo("CUSTOM");
    }

    @Test
    @DisplayName("应该正确处理空 lineage")
    void shouldHandleEmptyLineage() {
      // Given: 创建不带 lineage 的游标
      Instant instant = Instant.parse("2025-01-01T00:00:00Z");
      CursorValue value = CursorValue.time(instant);
      CursorWatermark watermark = new CursorWatermark(instant.toString(), instant, null);

      Cursor cursor =
          Cursor.restore(
              400L,
              PROVENANCE_CODE,
              OPERATION_CODE,
              CURSOR_KEY,
              NamespaceScope.GLOBAL,
              NAMESPACE_KEY,
              CursorType.TIME,
              value,
              watermark,
              CursorLineage.empty(),
              EXPR_HASH);

      // When: 转换为 DO
      CursorDO result = converter.toDO(cursor);

      // Then: 验证 lineage 字段为 null
      assertThat(result.getScheduleInstanceId()).isNull();
      assertThat(result.getPlanId()).isNull();
      assertThat(result.getSliceId()).isNull();
      assertThat(result.getTaskId()).isNull();
      assertThat(result.getLastRunId()).isNull();
      assertThat(result.getLastBatchId()).isNull();
    }
  }

  // ========== toDomain() 转换测试 ==========

  @Nested
  @DisplayName("toDomain() 转换测试")
  class ToDomainConversionTests {

    @Test
    @DisplayName("应该正确转换包含所有字段的 CursorDO")
    void shouldConvertFullDOToDomain() {
      // Given: 创建完整的 CursorDO
      Instant instant = Instant.parse("2025-01-15T10:30:00Z");
      BigDecimal numeric = BigDecimal.valueOf(12345);

      CursorDO cursorDO = new CursorDO();
      cursorDO.setId(100L);
      cursorDO.setProvenanceCode(PROVENANCE_CODE);
      cursorDO.setOperationCode(OPERATION_CODE);
      cursorDO.setCursorKey(CURSOR_KEY);
      cursorDO.setCursorTypeCode("TIME");
      cursorDO.setNamespaceScopeCode("GLOBAL");
      cursorDO.setNamespaceKey(NAMESPACE_KEY);
      cursorDO.setCursorValue(instant.toString());
      cursorDO.setNormalizedInstant(instant);
      cursorDO.setNormalizedNumeric(numeric);
      cursorDO.setObservedMaxValue(instant.toString());
      cursorDO.setScheduleInstanceId(1L);
      cursorDO.setPlanId(2L);
      cursorDO.setSliceId(3L);
      cursorDO.setTaskId(4L);
      cursorDO.setLastRunId(5L);
      cursorDO.setLastBatchId(6L);
      cursorDO.setExprHash(EXPR_HASH);

      // When: 转换为 Domain
      Cursor result = converter.toDomain(cursorDO);

      // Then: 验证转换结果
      assertThat(result).isNotNull();
      assertThat(result.getId()).isEqualTo(100L);
      assertThat(result.getProvenanceCode()).isEqualTo(PROVENANCE_CODE);
      assertThat(result.getOperationCode()).isEqualTo(OPERATION_CODE);
      assertThat(result.getCursorKey()).isEqualTo(CURSOR_KEY);
      assertThat(result.getCursorType()).isEqualTo(CursorType.TIME);
      assertThat(result.getNamespaceScope()).isEqualTo(NamespaceScope.GLOBAL);
      assertThat(result.getNamespaceKey()).isEqualTo(NAMESPACE_KEY);
      assertThat(result.getExprHash()).isEqualTo(EXPR_HASH);

      // 验证 CursorValue
      assertThat(result.getValue()).isNotNull();
      assertThat(result.getValue().raw()).isEqualTo(instant.toString());
      assertThat(result.getValue().instant()).isEqualTo(instant);
      assertThat(result.getValue().numeric()).isEqualByComparingTo(numeric);

      // 验证 CursorWatermark
      assertThat(result.getWatermark()).isNotNull();
      assertThat(result.getWatermark().observedMaxValue()).isEqualTo(instant.toString());
      assertThat(result.getWatermark().normalizedInstant()).isEqualTo(instant);
      assertThat(result.getWatermark().normalizedNumeric()).isEqualByComparingTo(numeric);

      // 验证 CursorLineage
      assertThat(result.getLineage()).isNotNull();
      assertThat(result.getLineage().scheduleInstanceId()).isEqualTo(1L);
      assertThat(result.getLineage().planId()).isEqualTo(2L);
      assertThat(result.getLineage().sliceId()).isEqualTo(3L);
      assertThat(result.getLineage().taskId()).isEqualTo(4L);
      assertThat(result.getLineage().runId()).isEqualTo(5L);
      assertThat(result.getLineage().batchId()).isEqualTo(6L);
    }

    @Test
    @DisplayName("应该正确转换 ID 类型 DO")
    void shouldConvertIdTypeDOToDomain() {
      // Given: 创建 ID 类型 DO
      BigDecimal numeric = BigDecimal.valueOf(54321);

      CursorDO cursorDO = new CursorDO();
      cursorDO.setId(200L);
      cursorDO.setProvenanceCode(PROVENANCE_CODE);
      cursorDO.setOperationCode(OPERATION_CODE);
      cursorDO.setCursorKey("record_id");
      cursorDO.setCursorTypeCode("ID");
      cursorDO.setNamespaceScopeCode("EXPR");
      cursorDO.setNamespaceKey(NAMESPACE_KEY);
      cursorDO.setCursorValue("54321");
      cursorDO.setNormalizedNumeric(numeric);
      cursorDO.setObservedMaxValue("54321");
      cursorDO.setExprHash(EXPR_HASH);

      // When: 转换为 Domain
      Cursor result = converter.toDomain(cursorDO);

      // Then: 验证 ID 类型字段
      assertThat(result.getCursorType()).isEqualTo(CursorType.ID);
      assertThat(result.getValue().numeric()).isEqualByComparingTo(numeric);
      assertThat(result.getValue().instant()).isNull();
      assertThat(result.getWatermark().normalizedNumeric()).isEqualByComparingTo(numeric);
      assertThat(result.getWatermark().normalizedInstant()).isNull();
      assertThat(result.getNamespaceScope()).isEqualTo(NamespaceScope.EXPR);
    }

    @Test
    @DisplayName("应该正确转换 TOKEN 类型 DO")
    void shouldConvertTokenTypeDOToDomain() {
      // Given: 创建 TOKEN 类型 DO
      CursorDO cursorDO = new CursorDO();
      cursorDO.setId(300L);
      cursorDO.setProvenanceCode(PROVENANCE_CODE);
      cursorDO.setOperationCode(OPERATION_CODE);
      cursorDO.setCursorKey("cursor_token");
      cursorDO.setCursorTypeCode("TOKEN");
      cursorDO.setNamespaceScopeCode("CUSTOM");
      cursorDO.setNamespaceKey(NAMESPACE_KEY);
      cursorDO.setCursorValue("token-value");
      cursorDO.setObservedMaxValue("token-max");
      cursorDO.setExprHash(EXPR_HASH);

      // When: 转换为 Domain
      Cursor result = converter.toDomain(cursorDO);

      // Then: 验证 TOKEN 类型字段
      assertThat(result.getCursorType()).isEqualTo(CursorType.TOKEN);
      assertThat(result.getValue().raw()).isEqualTo("token-value");
      assertThat(result.getValue().instant()).isNull();
      assertThat(result.getValue().numeric()).isNull();
      assertThat(result.getWatermark().observedMaxValue()).isEqualTo("token-max");
      assertThat(result.getNamespaceScope()).isEqualTo(NamespaceScope.CUSTOM);
    }

    @Test
    @DisplayName("应该正确处理 null CursorDO")
    void shouldReturnNullForNullDO() {
      // When: 转换 null DO
      Cursor result = converter.toDomain(null);

      // Then: 应返回 null
      assertThat(result).isNull();
    }
  }

  // ========== 静态辅助方法测试 ==========

  @Nested
  @DisplayName("静态辅助方法测试")
  class StaticHelperMethodTests {

    @Test
    @DisplayName("cursorTypeToCode() 应正确转换枚举")
    void shouldConvertCursorTypeToCode() {
      assertThat(CursorConverter.cursorTypeToCode(CursorType.TIME)).isEqualTo("TIME");
      assertThat(CursorConverter.cursorTypeToCode(CursorType.ID)).isEqualTo("ID");
      assertThat(CursorConverter.cursorTypeToCode(CursorType.TOKEN)).isEqualTo("TOKEN");
      assertThat(CursorConverter.cursorTypeToCode(null)).isNull();
    }

    @Test
    @DisplayName("cursorTypeFromCode() 应正确转换代码")
    void shouldConvertCodeToCursorType() {
      assertThat(CursorConverter.cursorTypeFromCode("TIME")).isEqualTo(CursorType.TIME);
      assertThat(CursorConverter.cursorTypeFromCode("ID")).isEqualTo(CursorType.ID);
      assertThat(CursorConverter.cursorTypeFromCode("TOKEN")).isEqualTo(CursorType.TOKEN);
      assertThat(CursorConverter.cursorTypeFromCode(null)).isNull();
    }

    @Test
    @DisplayName("namespaceScopeToCode() 应正确转换枚举")
    void shouldConvertNamespaceScopeToCode() {
      assertThat(CursorConverter.namespaceScopeToCode(NamespaceScope.GLOBAL)).isEqualTo("GLOBAL");
      assertThat(CursorConverter.namespaceScopeToCode(NamespaceScope.EXPR)).isEqualTo("EXPR");
      assertThat(CursorConverter.namespaceScopeToCode(NamespaceScope.CUSTOM)).isEqualTo("CUSTOM");
      assertThat(CursorConverter.namespaceScopeToCode(null)).isNull();
    }

    @Test
    @DisplayName("namespaceScopeFromCode() 应正确转换代码")
    void shouldConvertCodeToNamespaceScope() {
      assertThat(CursorConverter.namespaceScopeFromCode("GLOBAL")).isEqualTo(NamespaceScope.GLOBAL);
      assertThat(CursorConverter.namespaceScopeFromCode("EXPR")).isEqualTo(NamespaceScope.EXPR);
      assertThat(CursorConverter.namespaceScopeFromCode("CUSTOM")).isEqualTo(NamespaceScope.CUSTOM);
      assertThat(CursorConverter.namespaceScopeFromCode(null)).isNull();
    }

    @Test
    @DisplayName("normalizedInstant() 应优先使用 watermark")
    void shouldPrioritizeWatermarkInstant() {
      // Given: watermark 和 value 都有 instant
      Instant valueInstant = Instant.parse("2025-01-01T00:00:00Z");
      Instant watermarkInstant = Instant.parse("2025-01-01T12:00:00Z");

      CursorValue value = CursorValue.time(valueInstant);
      CursorWatermark watermark = new CursorWatermark("max", watermarkInstant, null);
      CursorLineage lineage = CursorLineage.empty();

      Cursor cursor =
          Cursor.restore(
              null,
              PROVENANCE_CODE,
              OPERATION_CODE,
              CURSOR_KEY,
              NamespaceScope.GLOBAL,
              NAMESPACE_KEY,
              CursorType.TIME,
              value,
              watermark,
              lineage,
              EXPR_HASH);

      // When: 获取规范化时间
      Instant result = CursorConverter.normalizedInstant(cursor);

      // Then: 应使用 watermark 的时间
      assertThat(result).isEqualTo(watermarkInstant);
    }

    @Test
    @DisplayName("normalizedNumeric() 应优先使用 watermark")
    void shouldPrioritizeWatermarkNumeric() {
      // Given: watermark 和 value 都有 numeric
      BigDecimal valueNumeric = BigDecimal.valueOf(100);
      BigDecimal watermarkNumeric = BigDecimal.valueOf(200);

      CursorValue value = CursorValue.id(valueNumeric);
      CursorWatermark watermark = new CursorWatermark("200", null, watermarkNumeric);
      CursorLineage lineage = CursorLineage.empty();

      Cursor cursor =
          Cursor.restore(
              null,
              PROVENANCE_CODE,
              OPERATION_CODE,
              CURSOR_KEY,
              NamespaceScope.GLOBAL,
              NAMESPACE_KEY,
              CursorType.ID,
              value,
              watermark,
              lineage,
              EXPR_HASH);

      // When: 获取规范化数值
      BigDecimal result = CursorConverter.normalizedNumeric(cursor);

      // Then: 应使用 watermark 的数值
      assertThat(result).isEqualByComparingTo(watermarkNumeric);
    }

    @Test
    @DisplayName("observedMaxValue() 应正确提取 watermark 值")
    void shouldExtractObservedMaxValue() {
      // Given: 创建带 watermark 的游标
      CursorValue value = CursorValue.token("test");
      CursorWatermark watermark = new CursorWatermark("max-value-xyz", null, null);
      CursorLineage lineage = CursorLineage.empty();

      Cursor cursor =
          Cursor.restore(
              null,
              PROVENANCE_CODE,
              OPERATION_CODE,
              CURSOR_KEY,
              NamespaceScope.GLOBAL,
              NAMESPACE_KEY,
              CursorType.TOKEN,
              value,
              watermark,
              lineage,
              EXPR_HASH);

      // When: 提取 observedMaxValue
      String result = CursorConverter.observedMaxValue(cursor);

      // Then: 应返回 watermark 的值
      assertThat(result).isEqualTo("max-value-xyz");
    }
  }

  // ========== Null 安全测试 ==========

  @Nested
  @DisplayName("Null 安全测试")
  class NullSafetyTests {

    @Test
    @DisplayName("normalizedInstant() 在 Cursor 为 null 时应返回 null")
    void shouldReturnNullWhenCursorNull() {
      assertThat(CursorConverter.normalizedInstant(null)).isNull();
    }

    @Test
    @DisplayName("normalizedNumeric() 在 Cursor 为 null 时应返回 null")
    void shouldReturnNullWhenCursorNullForNumeric() {
      assertThat(CursorConverter.normalizedNumeric(null)).isNull();
    }

    @Test
    @DisplayName("observedMaxValue() 在 Cursor 为 null 时应返回 null")
    void shouldReturnNullWhenCursorNullForObservedMax() {
      assertThat(CursorConverter.observedMaxValue(null)).isNull();
    }

    @Test
    @DisplayName("observedMaxValue() 在 watermark 为 null 时应返回 null")
    void shouldReturnNullWhenWatermarkNull() {
      // Given: watermark 为 null 的游标
      CursorValue value = CursorValue.token("test");
      CursorLineage lineage = CursorLineage.empty();

      Cursor cursor =
          Cursor.restore(
              null,
              PROVENANCE_CODE,
              OPERATION_CODE,
              CURSOR_KEY,
              NamespaceScope.GLOBAL,
              NAMESPACE_KEY,
              CursorType.TOKEN,
              value,
              null, // null watermark
              lineage,
              EXPR_HASH);

      // When: 提取 observedMaxValue
      String result = CursorConverter.observedMaxValue(cursor);

      // Then: 应返回 null
      assertThat(result).isNull();
    }
  }
}
