package dev.linqibin.patra.registry.domain.model.vo.provenance;

import static org.assertj.core.api.Assertions.*;

import dev.linqibin.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// PaginationConfig 值对象单元测试。
///
/// 测试策略：
///
/// - 纯 Java 单元测试，不依赖 Spring 容器
///   - 测试 record 的业务约束验证（正整数 ID、非空白字符串、必需字段等）
///   - 验证字符串字段自动 trim 处理
///   - 测试可选字段的 null 处理
///   - 遵循 Given-When-Then 结构
///   - 使用 AssertJ 流畅断言
///
/// 覆盖范围：
///
/// - ✅ record 构造函数验证测试
///   - ✅ 正整数 ID 验证（id, provenanceId）
///   - ✅ 非空白字符串验证（paginationModeCode）
///   - ✅ 必需字段非 null 验证（effectiveFrom）
///   - ✅ 字符串 trim 处理测试（operationType, paginationModeCode, sortFieldParamName）
///   - ✅ 可选字段处理（effectiveTo, pageSizeValue 等允许为 null）
///   - ✅ record 的 equals/hashCode/toString 测试
///   - ✅ 不变性保证
///   - ✅ 业务场景测试（不同操作类型、分页模式、排序配置等）
///   - ✅ 边界条件处理
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PaginationConfig 单元测试")
class PaginationConfigTest {

  // ========== Record 创建测试 ==========

  @Nested
  @DisplayName("Record 创建")
  class RecordCreationTests {

    @Test
    @DisplayName("应该成功创建包含所有字段的分页配置")
    void shouldCreatePaginationConfigWithAllFields() {
      // Given: 所有字段都有效
      Long id = 1001L;
      Long provenanceId = 2001L;
      String operationType = "HARVEST";
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-12-31T23:59:59Z");
      String paginationModeCode = "PAGE_NUMBER";
      Integer pageSizeValue = 100;
      Integer maxPagesPerExecution = 10;
      String sortFieldParamName = "updated_at";
      Integer sortingDirection = 0;

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              id,
              provenanceId,
              operationType,
              effectiveFrom,
              effectiveTo,
              paginationModeCode,
              pageSizeValue,
              maxPagesPerExecution,
              sortFieldParamName,
              sortingDirection);

      // Then: 验证所有字段正确赋值
      assertThat(paginationConfig).isNotNull();
      assertThat(paginationConfig.id()).isEqualTo(id);
      assertThat(paginationConfig.provenanceId()).isEqualTo(provenanceId);
      assertThat(paginationConfig.operationType()).isEqualTo(operationType);
      assertThat(paginationConfig.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(paginationConfig.effectiveTo()).isEqualTo(effectiveTo);
      assertThat(paginationConfig.paginationModeCode()).isEqualTo(paginationModeCode);
      assertThat(paginationConfig.pageSizeValue()).isEqualTo(pageSizeValue);
      assertThat(paginationConfig.maxPagesPerExecution()).isEqualTo(maxPagesPerExecution);
      assertThat(paginationConfig.sortFieldParamName()).isEqualTo(sortFieldParamName);
      assertThat(paginationConfig.sortingDirection()).isEqualTo(sortingDirection);
    }

    @Test
    @DisplayName("应该成功创建仅包含必需字段的最小配置")
    void shouldCreateMinimalPaginationConfig() {
      // Given: 只有必需字段
      Long id = 1001L;
      Long provenanceId = 2001L;
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      String paginationModeCode = "CURSOR";

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              id,
              provenanceId,
              null, // operationType
              effectiveFrom,
              null, // effectiveTo
              paginationModeCode,
              null, // pageSizeValue
              null, // maxPagesPerExecution
              null, // sortFieldParamName
              null // sortingDirection
              );

      // Then: 验证必需字段正确赋值，可选字段为 null
      assertThat(paginationConfig).isNotNull();
      assertThat(paginationConfig.id()).isEqualTo(id);
      assertThat(paginationConfig.provenanceId()).isEqualTo(provenanceId);
      assertThat(paginationConfig.operationType()).isNull();
      assertThat(paginationConfig.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(paginationConfig.effectiveTo()).isNull();
      assertThat(paginationConfig.paginationModeCode()).isEqualTo(paginationModeCode);
      assertThat(paginationConfig.pageSizeValue()).isNull();
      assertThat(paginationConfig.maxPagesPerExecution()).isNull();
      assertThat(paginationConfig.sortFieldParamName()).isNull();
      assertThat(paginationConfig.sortingDirection()).isNull();
    }

    @Test
    @DisplayName("应该成功创建 effectiveTo 为 null 的永久有效配置")
    void shouldCreatePermanentConfig() {
      // Given: effectiveTo 为 null（表示永久有效）
      Long id = 1001L;
      Long provenanceId = 2001L;
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      Instant effectiveTo = null;

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              id,
              provenanceId,
              "HARVEST",
              effectiveFrom,
              effectiveTo,
              "PAGE_NUMBER",
              50,
              null,
              null,
              null);

      // Then: 验证 effectiveTo 为 null
      assertThat(paginationConfig.effectiveTo()).isNull();
    }
  }

  // ========== ID 验证测试 ==========

  @Nested
  @DisplayName("ID 正整数验证")
  class IdValidationTests {

    @Test
    @DisplayName("应该抛出异常当 id 为 null")
    void shouldThrowExceptionWhenIdIsNull() {
      // Given: id 为 null
      Long id = null;

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new PaginationConfig(
                      id,
                      2001L,
                      "HARVEST",
                      Instant.now(),
                      null,
                      "PAGE_NUMBER",
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Pagination config id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该抛出异常当 id 为 0")
    void shouldThrowExceptionWhenIdIsZero() {
      // Given: id 为 0
      Long id = 0L;

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new PaginationConfig(
                      id,
                      2001L,
                      "HARVEST",
                      Instant.now(),
                      null,
                      "PAGE_NUMBER",
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Pagination config id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该抛出异常当 id 为负数")
    void shouldThrowExceptionWhenIdIsNegative() {
      // Given: id 为负数
      Long id = -1L;

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new PaginationConfig(
                      id,
                      2001L,
                      "HARVEST",
                      Instant.now(),
                      null,
                      "PAGE_NUMBER",
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Pagination config id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该成功创建 id 为 1 的配置")
    void shouldCreateConfigWithIdOne() {
      // Given: id 为 1
      Long id = 1L;

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              id, 2001L, "HARVEST", Instant.now(), null, "PAGE_NUMBER", null, null, null, null);

      // Then: 验证成功创建
      assertThat(paginationConfig.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("应该成功创建 id 为 Long.MAX_VALUE 的配置")
    void shouldCreateConfigWithMaxId() {
      // Given: id 为 Long.MAX_VALUE
      Long id = Long.MAX_VALUE;

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              id, 2001L, "HARVEST", Instant.now(), null, "PAGE_NUMBER", null, null, null, null);

      // Then: 验证成功创建
      assertThat(paginationConfig.id()).isEqualTo(Long.MAX_VALUE);
    }
  }

  @Nested
  @DisplayName("ProvenanceId 正整数验证")
  class ProvenanceIdValidationTests {

    @Test
    @DisplayName("应该抛出异常当 provenanceId 为 null")
    void shouldThrowExceptionWhenProvenanceIdIsNull() {
      // Given: provenanceId 为 null
      Long provenanceId = null;

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new PaginationConfig(
                      1001L,
                      provenanceId,
                      "HARVEST",
                      Instant.now(),
                      null,
                      "PAGE_NUMBER",
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该抛出异常当 provenanceId 为 0")
    void shouldThrowExceptionWhenProvenanceIdIsZero() {
      // Given: provenanceId 为 0
      Long provenanceId = 0L;

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new PaginationConfig(
                      1001L,
                      provenanceId,
                      "HARVEST",
                      Instant.now(),
                      null,
                      "PAGE_NUMBER",
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该抛出异常当 provenanceId 为负数")
    void shouldThrowExceptionWhenProvenanceIdIsNegative() {
      // Given: provenanceId 为负数
      Long provenanceId = -1L;

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new PaginationConfig(
                      1001L,
                      provenanceId,
                      "HARVEST",
                      Instant.now(),
                      null,
                      "PAGE_NUMBER",
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance id")
          .hasMessageContaining("必须为正数");
    }

    @Test
    @DisplayName("应该成功创建 provenanceId 为 1 的配置")
    void shouldCreateConfigWithProvenanceIdOne() {
      // Given: provenanceId 为 1
      Long provenanceId = 1L;

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              provenanceId,
              "HARVEST",
              Instant.now(),
              null,
              "PAGE_NUMBER",
              null,
              null,
              null,
              null);

      // Then: 验证成功创建
      assertThat(paginationConfig.provenanceId()).isEqualTo(1L);
    }
  }

  // ========== EffectiveFrom 验证测试 ==========

  @Nested
  @DisplayName("EffectiveFrom 非 null 验证")
  class EffectiveFromValidationTests {

    @Test
    @DisplayName("应该抛出异常当 effectiveFrom 为 null")
    void shouldThrowExceptionWhenEffectiveFromIsNull() {
      // Given: effectiveFrom 为 null
      Instant effectiveFrom = null;

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new PaginationConfig(
                      1001L,
                      2001L,
                      "HARVEST",
                      effectiveFrom,
                      null,
                      "PAGE_NUMBER",
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Effective from")
          .hasMessageContaining("不能为 null");
    }

    @Test
    @DisplayName("应该成功创建 effectiveFrom 为当前时间的配置")
    void shouldCreateConfigWithCurrentEffectiveFrom() {
      // Given: effectiveFrom 为当前时间
      Instant effectiveFrom = Instant.now();

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L, 2001L, "HARVEST", effectiveFrom, null, "PAGE_NUMBER", null, null, null, null);

      // Then: 验证成功创建
      assertThat(paginationConfig.effectiveFrom()).isEqualTo(effectiveFrom);
    }

    @Test
    @DisplayName("应该成功创建 effectiveFrom 为过去时间的配置")
    void shouldCreateConfigWithPastEffectiveFrom() {
      // Given: effectiveFrom 为过去时间
      Instant effectiveFrom = Instant.parse("2024-01-01T00:00:00Z");

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L, 2001L, "HARVEST", effectiveFrom, null, "PAGE_NUMBER", null, null, null, null);

      // Then: 验证成功创建
      assertThat(paginationConfig.effectiveFrom()).isEqualTo(effectiveFrom);
    }

    @Test
    @DisplayName("应该成功创建 effectiveFrom 为未来时间的配置")
    void shouldCreateConfigWithFutureEffectiveFrom() {
      // Given: effectiveFrom 为未来时间
      Instant effectiveFrom = Instant.parse("2026-01-01T00:00:00Z");

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L, 2001L, "HARVEST", effectiveFrom, null, "PAGE_NUMBER", null, null, null, null);

      // Then: 验证成功创建
      assertThat(paginationConfig.effectiveFrom()).isEqualTo(effectiveFrom);
    }
  }

  // ========== PaginationModeCode 验证测试 ==========

  @Nested
  @DisplayName("PaginationModeCode 非空白验证")
  class PaginationModeCodeValidationTests {

    @Test
    @DisplayName("应该抛出异常当 paginationModeCode 为 null")
    void shouldThrowExceptionWhenPaginationModeCodeIsNull() {
      // Given: paginationModeCode 为 null
      String paginationModeCode = null;

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new PaginationConfig(
                      1001L,
                      2001L,
                      "HARVEST",
                      Instant.now(),
                      null,
                      paginationModeCode,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Pagination mode code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 paginationModeCode 为空字符串")
    void shouldThrowExceptionWhenPaginationModeCodeIsEmpty() {
      // Given: paginationModeCode 为空字符串
      String paginationModeCode = "";

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new PaginationConfig(
                      1001L,
                      2001L,
                      "HARVEST",
                      Instant.now(),
                      null,
                      paginationModeCode,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Pagination mode code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该抛出异常当 paginationModeCode 仅包含空白字符")
    void shouldThrowExceptionWhenPaginationModeCodeIsBlank() {
      // Given: paginationModeCode 仅包含空白字符
      String paginationModeCode = "   ";

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () ->
                  new PaginationConfig(
                      1001L,
                      2001L,
                      "HARVEST",
                      Instant.now(),
                      null,
                      paginationModeCode,
                      null,
                      null,
                      null,
                      null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Pagination mode code")
          .hasMessageContaining("不能为空白");
    }

    @Test
    @DisplayName("应该自动 trim paginationModeCode 字段")
    void shouldTrimPaginationModeCode() {
      // Given: paginationModeCode 包含首尾空白
      String paginationModeCode = "  PAGE_NUMBER  ";

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              paginationModeCode,
              null,
              null,
              null,
              null);

      // Then: 验证 paginationModeCode 已被 trim
      assertThat(paginationConfig.paginationModeCode()).isEqualTo("PAGE_NUMBER");
    }
  }

  // ========== 字符串 Trim 测试 ==========

  @Nested
  @DisplayName("字符串字段 Trim 处理")
  class StringTrimTests {

    @Test
    @DisplayName("应该自动 trim operationType 字段")
    void shouldTrimOperationType() {
      // Given: operationType 包含首尾空白
      String operationType = "  HARVEST  ";

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              operationType,
              Instant.now(),
              null,
              "PAGE_NUMBER",
              null,
              null,
              null,
              null);

      // Then: 验证 operationType 已被 trim
      assertThat(paginationConfig.operationType()).isEqualTo("HARVEST");
    }

    @Test
    @DisplayName("应该自动 trim sortFieldParamName 字段")
    void shouldTrimSortFieldParamName() {
      // Given: sortFieldParamName 包含首尾空白
      String sortFieldParamName = "  updated_at  ";

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "PAGE_NUMBER",
              null,
              null,
              sortFieldParamName,
              null);

      // Then: 验证 sortFieldParamName 已被 trim
      assertThat(paginationConfig.sortFieldParamName()).isEqualTo("updated_at");
    }

    @Test
    @DisplayName("应该 trim 所有字符串字段")
    void shouldTrimAllStringFields() {
      // Given: 所有字符串字段包含首尾空白
      String operationType = "  UPDATE  ";
      String paginationModeCode = "  CURSOR  ";
      String sortFieldParamName = "  created_at  ";

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              operationType,
              Instant.now(),
              null,
              paginationModeCode,
              null,
              null,
              sortFieldParamName,
              null);

      // Then: 验证所有字段都已被 trim
      assertThat(paginationConfig.operationType()).isEqualTo("UPDATE");
      assertThat(paginationConfig.paginationModeCode()).isEqualTo("CURSOR");
      assertThat(paginationConfig.sortFieldParamName()).isEqualTo("created_at");
    }

    @Test
    @DisplayName("应该处理混合空白字符的字符串")
    void shouldHandleStringWithMixedWhitespace() {
      // Given: 包含制表符、换行符等混合空白的字符串
      String operationType = "\t\n  BACKFILL  \t\n";
      String paginationModeCode = " \t TOKEN \n ";

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              operationType,
              Instant.now(),
              null,
              paginationModeCode,
              null,
              null,
              null,
              null);

      // Then: 验证空白字符都被 trim
      assertThat(paginationConfig.operationType()).isEqualTo("BACKFILL");
      assertThat(paginationConfig.paginationModeCode()).isEqualTo("TOKEN");
    }

    @Test
    @DisplayName("应该保留字符串内部的空白字符")
    void shouldPreserveInternalWhitespace() {
      // Given: 字符串内部包含空白字符
      String sortFieldParamName = "  last modified date  ";

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "PAGE_NUMBER",
              null,
              null,
              sortFieldParamName,
              null);

      // Then: 验证内部空白字符被保留
      assertThat(paginationConfig.sortFieldParamName()).isEqualTo("last modified date");
    }
  }

  // ========== Null 处理测试 ==========

  @Nested
  @DisplayName("可选字段 Null 处理")
  class NullHandlingTests {

    @Test
    @DisplayName("operationType 为 null 时应保持 null")
    void operationTypeCanBeNull() {
      // Given: operationType 为 null
      String operationType = null;

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              operationType,
              Instant.now(),
              null,
              "PAGE_NUMBER",
              null,
              null,
              null,
              null);

      // Then: 验证 operationType 为 null
      assertThat(paginationConfig.operationType()).isNull();
    }

    @Test
    @DisplayName("effectiveTo 为 null 时应保持 null（表示永久有效）")
    void effectiveToCanBeNull() {
      // Given: effectiveTo 为 null
      Instant effectiveTo = null;

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              effectiveTo,
              "PAGE_NUMBER",
              null,
              null,
              null,
              null);

      // Then: 验证 effectiveTo 为 null
      assertThat(paginationConfig.effectiveTo()).isNull();
    }

    @Test
    @DisplayName("pageSizeValue 为 null 时应允许")
    void pageSizeValueCanBeNull() {
      // Given: pageSizeValue 为 null
      Integer pageSizeValue = null;

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "PAGE_NUMBER",
              pageSizeValue,
              null,
              null,
              null);

      // Then: 验证 pageSizeValue 为 null
      assertThat(paginationConfig.pageSizeValue()).isNull();
    }

    @Test
    @DisplayName("maxPagesPerExecution 为 null 时应允许")
    void maxPagesPerExecutionCanBeNull() {
      // Given: maxPagesPerExecution 为 null
      Integer maxPagesPerExecution = null;

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "PAGE_NUMBER",
              null,
              maxPagesPerExecution,
              null,
              null);

      // Then: 验证 maxPagesPerExecution 为 null
      assertThat(paginationConfig.maxPagesPerExecution()).isNull();
    }

    @Test
    @DisplayName("sortFieldParamName 为 null 时应保持 null")
    void sortFieldParamNameCanBeNull() {
      // Given: sortFieldParamName 为 null
      String sortFieldParamName = null;

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "PAGE_NUMBER",
              null,
              null,
              sortFieldParamName,
              null);

      // Then: 验证 sortFieldParamName 为 null
      assertThat(paginationConfig.sortFieldParamName()).isNull();
    }

    @Test
    @DisplayName("sortingDirection 为 null 时应允许")
    void sortingDirectionCanBeNull() {
      // Given: sortingDirection 为 null
      Integer sortingDirection = null;

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "PAGE_NUMBER",
              null,
              null,
              null,
              sortingDirection);

      // Then: 验证 sortingDirection 为 null
      assertThat(paginationConfig.sortingDirection()).isNull();
    }

    @Test
    @DisplayName("应该处理所有可选字段都为 null 的情况")
    void shouldHandleAllOptionalFieldsBeingNull() {
      // Given: 所有可选字段为 null
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L, 2001L, null, Instant.now(), null, "PAGE_NUMBER", null, null, null, null);

      // Then: 验证可选字段都为 null
      assertThat(paginationConfig.operationType()).isNull();
      assertThat(paginationConfig.effectiveTo()).isNull();
      assertThat(paginationConfig.pageSizeValue()).isNull();
      assertThat(paginationConfig.maxPagesPerExecution()).isNull();
      assertThat(paginationConfig.sortFieldParamName()).isNull();
      assertThat(paginationConfig.sortingDirection()).isNull();
    }
  }

  // ========== Record 语义测试 ==========

  @Nested
  @DisplayName("Record 语义")
  class RecordSemanticsTests {

    @Test
    @DisplayName("应该正确实现 equals 方法（相同值对象相等）")
    void shouldImplementEqualsCorrectly() {
      // Given: 两个相同值的配置
      Long id = 1001L;
      Long provenanceId = 2001L;
      String operationType = "HARVEST";
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      String paginationModeCode = "PAGE_NUMBER";

      PaginationConfig paginationConfig1 =
          new PaginationConfig(
              id,
              provenanceId,
              operationType,
              effectiveFrom,
              null,
              paginationModeCode,
              100,
              10,
              "updated_at",
              0);

      PaginationConfig paginationConfig2 =
          new PaginationConfig(
              id,
              provenanceId,
              operationType,
              effectiveFrom,
              null,
              paginationModeCode,
              100,
              10,
              "updated_at",
              0);

      // When & Then: 应该相等
      assertThat(paginationConfig1).isEqualTo(paginationConfig2);
      assertThat(paginationConfig1).hasSameHashCodeAs(paginationConfig2);
    }

    @Test
    @DisplayName("应该正确实现 equals 方法（不同值对象不相等）")
    void shouldImplementEqualsCorrectlyForDifferentObjects() {
      // Given: 两个不同值的配置
      PaginationConfig paginationConfig1 =
          new PaginationConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, "PAGE_NUMBER", null, null, null, null);

      PaginationConfig paginationConfig2 =
          new PaginationConfig(
              1002L, 2002L, "UPDATE", Instant.now(), null, "CURSOR", null, null, null, null);

      // When & Then: 不应该相等
      assertThat(paginationConfig1).isNotEqualTo(paginationConfig2);
    }

    @Test
    @DisplayName("应该正确实现 hashCode 方法")
    void shouldImplementHashCodeCorrectly() {
      // Given: 两个相同值的配置
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      PaginationConfig paginationConfig1 =
          new PaginationConfig(
              1001L, 2001L, "HARVEST", effectiveFrom, null, "PAGE_NUMBER", null, null, null, null);

      PaginationConfig paginationConfig2 =
          new PaginationConfig(
              1001L, 2001L, "HARVEST", effectiveFrom, null, "PAGE_NUMBER", null, null, null, null);

      // When & Then: hashCode 应该相等
      assertThat(paginationConfig1.hashCode()).isEqualTo(paginationConfig2.hashCode());
    }

    @Test
    @DisplayName("应该正确实现 toString 方法")
    void shouldImplementToStringCorrectly() {
      // Given: 创建配置
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.parse("2025-01-01T00:00:00Z"),
              null,
              "PAGE_NUMBER",
              100,
              10,
              "updated_at",
              0);

      // When: 调用 toString
      String toString = paginationConfig.toString();

      // Then: 应该包含关键字段
      assertThat(toString).contains("PaginationConfig");
      assertThat(toString).contains("1001");
      assertThat(toString).contains("2001");
      assertThat(toString).contains("HARVEST");
      assertThat(toString).contains("PAGE_NUMBER");
    }

    @Test
    @DisplayName("应该支持 equals 自反性")
    void shouldSupportEqualsReflexivity() {
      // Given: 创建配置
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, "PAGE_NUMBER", null, null, null, null);

      // When & Then: 对象应该等于自身
      assertThat(paginationConfig).isEqualTo(paginationConfig);
    }

    @Test
    @DisplayName("应该支持 equals 对称性")
    void shouldSupportEqualsSymmetry() {
      // Given: 两个相同值的配置
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      PaginationConfig paginationConfig1 =
          new PaginationConfig(
              1001L, 2001L, "HARVEST", effectiveFrom, null, "PAGE_NUMBER", null, null, null, null);

      PaginationConfig paginationConfig2 =
          new PaginationConfig(
              1001L, 2001L, "HARVEST", effectiveFrom, null, "PAGE_NUMBER", null, null, null, null);

      // When & Then: 对称性（a.equals(b) == b.equals(a)）
      assertThat(paginationConfig1.equals(paginationConfig2))
          .isEqualTo(paginationConfig2.equals(paginationConfig1));
      assertThat(paginationConfig1).isEqualTo(paginationConfig2);
      assertThat(paginationConfig2).isEqualTo(paginationConfig1);
    }

    @Test
    @DisplayName("应该支持 equals 传递性")
    void shouldSupportEqualsTransitivity() {
      // Given: 三个相同值的配置
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      PaginationConfig paginationConfig1 =
          new PaginationConfig(
              1001L, 2001L, "HARVEST", effectiveFrom, null, "PAGE_NUMBER", null, null, null, null);

      PaginationConfig paginationConfig2 =
          new PaginationConfig(
              1001L, 2001L, "HARVEST", effectiveFrom, null, "PAGE_NUMBER", null, null, null, null);

      PaginationConfig paginationConfig3 =
          new PaginationConfig(
              1001L, 2001L, "HARVEST", effectiveFrom, null, "PAGE_NUMBER", null, null, null, null);

      // When & Then: 传递性（a.equals(b) && b.equals(c) => a.equals(c)）
      assertThat(paginationConfig1).isEqualTo(paginationConfig2);
      assertThat(paginationConfig2).isEqualTo(paginationConfig3);
      assertThat(paginationConfig1).isEqualTo(paginationConfig3);
    }

    @Test
    @DisplayName("应该正确处理与 null 的比较")
    void shouldHandleNullComparison() {
      // Given: 创建配置
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, "PAGE_NUMBER", null, null, null, null);

      // When & Then: 与 null 比较应该返回 false
      assertThat(paginationConfig).isNotEqualTo(null);
    }

    @Test
    @DisplayName("应该正确处理与不同类型对象的比较")
    void shouldHandleDifferentTypeComparison() {
      // Given: 创建配置
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, "PAGE_NUMBER", null, null, null, null);

      // When & Then: 与不同类型对象比较应该返回 false
      assertThat(paginationConfig).isNotEqualTo("Not a PaginationConfig");
      assertThat(paginationConfig).isNotEqualTo(1001L);
    }
  }

  // ========== 不变性测试 ==========

  @Nested
  @DisplayName("不变性保证")
  class ImmutabilityTests {

    @Test
    @DisplayName("Record 字段应该是不可变的")
    void recordFieldsShouldBeImmutable() {
      // Given: 创建配置
      Long originalId = 1001L;
      Long originalProvenanceId = 2001L;
      String originalOperationType = "HARVEST";
      Instant originalEffectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      String originalPaginationModeCode = "PAGE_NUMBER";

      PaginationConfig paginationConfig =
          new PaginationConfig(
              originalId,
              originalProvenanceId,
              originalOperationType,
              originalEffectiveFrom,
              null,
              originalPaginationModeCode,
              100,
              10,
              "updated_at",
              0);

      // When: 获取字段值
      Long retrievedId = paginationConfig.id();
      Long retrievedProvenanceId = paginationConfig.provenanceId();
      String retrievedOperationType = paginationConfig.operationType();
      Instant retrievedEffectiveFrom = paginationConfig.effectiveFrom();
      String retrievedPaginationModeCode = paginationConfig.paginationModeCode();

      // Then: 字段值应该保持不变
      assertThat(retrievedId).isEqualTo(originalId);
      assertThat(retrievedProvenanceId).isEqualTo(originalProvenanceId);
      assertThat(retrievedOperationType).isEqualTo(originalOperationType);
      assertThat(retrievedEffectiveFrom).isEqualTo(originalEffectiveFrom);
      assertThat(retrievedPaginationModeCode).isEqualTo(originalPaginationModeCode);
    }

    @Test
    @DisplayName("字符串字段应该在创建后保持不变")
    void stringFieldsShouldRemainUnchangedAfterCreation() {
      // Given: 创建配置
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "PAGE_NUMBER",
              null,
              null,
              "updated_at",
              null);

      // When: 多次获取字段值
      String operationType1 = paginationConfig.operationType();
      String operationType2 = paginationConfig.operationType();
      String paginationModeCode1 = paginationConfig.paginationModeCode();
      String paginationModeCode2 = paginationConfig.paginationModeCode();

      // Then: 字段值应该保持一致
      assertThat(operationType1).isEqualTo(operationType2);
      assertThat(paginationModeCode1).isEqualTo(paginationModeCode2);
      assertThat(operationType1).isSameAs(operationType2);
      assertThat(paginationModeCode1).isSameAs(paginationModeCode2);
    }
  }

  // ========== 业务场景测试 ==========

  @Nested
  @DisplayName("业务场景测试")
  class BusinessScenariosTests {

    @Test
    @DisplayName("应该成功创建 operationType 为 HARVEST 的配置")
    void shouldCreateConfigWithOperationTypeHarvest() {
      // Given: operationType 为 HARVEST
      String operationType = "HARVEST";

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              operationType,
              Instant.now(),
              null,
              "PAGE_NUMBER",
              null,
              null,
              null,
              null);

      // Then: 验证成功创建
      assertThat(paginationConfig.operationType()).isEqualTo("HARVEST");
    }

    @Test
    @DisplayName("应该成功创建 operationType 为 UPDATE 的配置")
    void shouldCreateConfigWithOperationTypeUpdate() {
      // Given: operationType 为 UPDATE
      String operationType = "UPDATE";

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              operationType,
              Instant.now(),
              null,
              "PAGE_NUMBER",
              null,
              null,
              null,
              null);

      // Then: 验证成功创建
      assertThat(paginationConfig.operationType()).isEqualTo("UPDATE");
    }

    @Test
    @DisplayName("应该成功创建 operationType 为 BACKFILL 的配置")
    void shouldCreateConfigWithOperationTypeBackfill() {
      // Given: operationType 为 BACKFILL
      String operationType = "BACKFILL";

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              operationType,
              Instant.now(),
              null,
              "PAGE_NUMBER",
              null,
              null,
              null,
              null);

      // Then: 验证成功创建
      assertThat(paginationConfig.operationType()).isEqualTo("BACKFILL");
    }

    @Test
    @DisplayName("应该成功创建 paginationModeCode 为 PAGE_NUMBER 的配置")
    void shouldCreateConfigWithPaginationModePageNumber() {
      // Given: paginationModeCode 为 PAGE_NUMBER
      String paginationModeCode = "PAGE_NUMBER";

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              paginationModeCode,
              50,
              null,
              null,
              null);

      // Then: 验证成功创建
      assertThat(paginationConfig.paginationModeCode()).isEqualTo("PAGE_NUMBER");
      assertThat(paginationConfig.pageSizeValue()).isEqualTo(50);
    }

    @Test
    @DisplayName("应该成功创建 paginationModeCode 为 CURSOR 的配置")
    void shouldCreateConfigWithPaginationModeCursor() {
      // Given: paginationModeCode 为 CURSOR
      String paginationModeCode = "CURSOR";

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              paginationModeCode,
              100,
              null,
              null,
              null);

      // Then: 验证成功创建
      assertThat(paginationConfig.paginationModeCode()).isEqualTo("CURSOR");
    }

    @Test
    @DisplayName("应该成功创建 paginationModeCode 为 TOKEN 的配置")
    void shouldCreateConfigWithPaginationModeToken() {
      // Given: paginationModeCode 为 TOKEN
      String paginationModeCode = "TOKEN";

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              paginationModeCode,
              null,
              null,
              null,
              null);

      // Then: 验证成功创建
      assertThat(paginationConfig.paginationModeCode()).isEqualTo("TOKEN");
    }

    @Test
    @DisplayName("应该成功创建 paginationModeCode 为 SCROLL 的配置")
    void shouldCreateConfigWithPaginationModeScroll() {
      // Given: paginationModeCode 为 SCROLL
      String paginationModeCode = "SCROLL";

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              paginationModeCode,
              1000,
              null,
              null,
              null);

      // Then: 验证成功创建
      assertThat(paginationConfig.paginationModeCode()).isEqualTo("SCROLL");
    }

    @Test
    @DisplayName("应该成功创建 sortingDirection 为 0（降序 DESC）的配置")
    void shouldCreateConfigWithSortingDirectionDesc() {
      // Given: sortingDirection 为 0（降序 DESC）
      Integer sortingDirection = 0;

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "PAGE_NUMBER",
              null,
              null,
              "updated_at",
              sortingDirection);

      // Then: 验证成功创建
      assertThat(paginationConfig.sortingDirection()).isEqualTo(0);
    }

    @Test
    @DisplayName("应该成功创建 sortingDirection 为 1（升序 ASC）的配置")
    void shouldCreateConfigWithSortingDirectionAsc() {
      // Given: sortingDirection 为 1（升序 ASC）
      Integer sortingDirection = 1;

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "PAGE_NUMBER",
              null,
              null,
              "created_at",
              sortingDirection);

      // Then: 验证成功创建
      assertThat(paginationConfig.sortingDirection()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该成功创建 pageSizeValue 为 10 的小批量配置")
    void shouldCreateConfigWithPageSize10() {
      // Given: pageSizeValue 为 10
      Integer pageSizeValue = 10;

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "PAGE_NUMBER",
              pageSizeValue,
              null,
              null,
              null);

      // Then: 验证成功创建
      assertThat(paginationConfig.pageSizeValue()).isEqualTo(10);
    }

    @Test
    @DisplayName("应该成功创建 pageSizeValue 为 50 的标准配置")
    void shouldCreateConfigWithPageSize50() {
      // Given: pageSizeValue 为 50
      Integer pageSizeValue = 50;

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "PAGE_NUMBER",
              pageSizeValue,
              null,
              null,
              null);

      // Then: 验证成功创建
      assertThat(paginationConfig.pageSizeValue()).isEqualTo(50);
    }

    @Test
    @DisplayName("应该成功创建 pageSizeValue 为 100 的大批量配置")
    void shouldCreateConfigWithPageSize100() {
      // Given: pageSizeValue 为 100
      Integer pageSizeValue = 100;

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "PAGE_NUMBER",
              pageSizeValue,
              null,
              null,
              null);

      // Then: 验证成功创建
      assertThat(paginationConfig.pageSizeValue()).isEqualTo(100);
    }

    @Test
    @DisplayName("应该成功创建 pageSizeValue 为 1000 的超大批量配置")
    void shouldCreateConfigWithPageSize1000() {
      // Given: pageSizeValue 为 1000
      Integer pageSizeValue = 1000;

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "CURSOR",
              pageSizeValue,
              null,
              null,
              null);

      // Then: 验证成功创建
      assertThat(paginationConfig.pageSizeValue()).isEqualTo(1000);
    }

    @Test
    @DisplayName("应该成功创建限制深度分页的配置")
    void shouldCreateConfigWithMaxPagesLimit() {
      // Given: maxPagesPerExecution 限制深度分页
      Integer maxPagesPerExecution = 5;

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "PAGE_NUMBER",
              100,
              maxPagesPerExecution,
              null,
              null);

      // Then: 验证深度分页限制
      assertThat(paginationConfig.maxPagesPerExecution()).isEqualTo(5);
    }

    @Test
    @DisplayName("应该成功创建完整的分页配置")
    void shouldCreateCompleteConfig() {
      // Given: 完整的分页配置
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.parse("2025-01-01T00:00:00Z"),
              Instant.parse("2025-12-31T23:59:59Z"),
              "PAGE_NUMBER",
              100,
              10,
              "updated_at",
              0);

      // Then: 验证所有字段
      assertThat(paginationConfig.id()).isEqualTo(1001L);
      assertThat(paginationConfig.provenanceId()).isEqualTo(2001L);
      assertThat(paginationConfig.operationType()).isEqualTo("HARVEST");
      assertThat(paginationConfig.paginationModeCode()).isEqualTo("PAGE_NUMBER");
      assertThat(paginationConfig.pageSizeValue()).isEqualTo(100);
      assertThat(paginationConfig.maxPagesPerExecution()).isEqualTo(10);
      assertThat(paginationConfig.sortFieldParamName()).isEqualTo("updated_at");
      assertThat(paginationConfig.sortingDirection()).isEqualTo(0);
    }

    @Test
    @DisplayName("应该成功创建无排序的分页配置")
    void shouldCreateConfigWithoutSorting() {
      // Given: 不需要排序的配置
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L, 2001L, "HARVEST", Instant.now(), null, "CURSOR", 50, null, null, null);

      // Then: 验证排序字段为 null
      assertThat(paginationConfig.sortFieldParamName()).isNull();
      assertThat(paginationConfig.sortingDirection()).isNull();
    }
  }

  // ========== 边界条件测试 ==========

  @Nested
  @DisplayName("边界条件处理")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该处理 operationType 为极短字符串的情况")
    void shouldHandleMinimalOperationType() {
      // Given: operationType 为单字符
      String operationType = "A";

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              operationType,
              Instant.now(),
              null,
              "PAGE_NUMBER",
              null,
              null,
              null,
              null);

      // Then: 应该成功创建
      assertThat(paginationConfig.operationType()).isEqualTo("A");
    }

    @Test
    @DisplayName("应该处理 paginationModeCode 为极短字符串的情况")
    void shouldHandleMinimalPaginationModeCode() {
      // Given: paginationModeCode 为单字符
      String paginationModeCode = "X";

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              paginationModeCode,
              null,
              null,
              null,
              null);

      // Then: 应该成功创建
      assertThat(paginationConfig.paginationModeCode()).isEqualTo("X");
    }

    @Test
    @DisplayName("应该处理 trim 后相同的不同输入")
    void shouldHandleDifferentInputsWithSameTrimmedValue() {
      // Given: trim 后相同的不同输入
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");

      PaginationConfig paginationConfig1 =
          new PaginationConfig(
              1001L, 2001L, "HARVEST", effectiveFrom, null, "PAGE_NUMBER", null, null, null, null);

      PaginationConfig paginationConfig2 =
          new PaginationConfig(
              1001L,
              2001L,
              "  HARVEST  ",
              effectiveFrom,
              null,
              "  PAGE_NUMBER  ",
              null,
              null,
              null,
              null);

      // When & Then: trim 后应该相等
      assertThat(paginationConfig1).isEqualTo(paginationConfig2);
    }

    @Test
    @DisplayName("应该处理 pageSizeValue 为 1 的情况")
    void shouldHandleMinimalPageSize() {
      // Given: pageSizeValue 为 1
      Integer pageSizeValue = 1;

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "PAGE_NUMBER",
              pageSizeValue,
              null,
              null,
              null);

      // Then: 应该成功创建
      assertThat(paginationConfig.pageSizeValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该处理 pageSizeValue 为 Integer.MAX_VALUE 的情况")
    void shouldHandleMaxPageSize() {
      // Given: pageSizeValue 为 Integer.MAX_VALUE
      Integer pageSizeValue = Integer.MAX_VALUE;

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "PAGE_NUMBER",
              pageSizeValue,
              null,
              null,
              null);

      // Then: 应该成功创建
      assertThat(paginationConfig.pageSizeValue()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("应该处理 maxPagesPerExecution 为 1 的情况")
    void shouldHandleMinimalMaxPages() {
      // Given: maxPagesPerExecution 为 1
      Integer maxPagesPerExecution = 1;

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "PAGE_NUMBER",
              null,
              maxPagesPerExecution,
              null,
              null);

      // Then: 应该成功创建
      assertThat(paginationConfig.maxPagesPerExecution()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该处理 maxPagesPerExecution 为 Integer.MAX_VALUE 的情况")
    void shouldHandleMaxPagesMaxValue() {
      // Given: maxPagesPerExecution 为 Integer.MAX_VALUE
      Integer maxPagesPerExecution = Integer.MAX_VALUE;

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "PAGE_NUMBER",
              null,
              maxPagesPerExecution,
              null,
              null);

      // Then: 应该成功创建
      assertThat(paginationConfig.maxPagesPerExecution()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("应该处理 sortFieldParamName 包含特殊字符的情况")
    void shouldHandleSortFieldWithSpecialCharacters() {
      // Given: sortFieldParamName 包含特殊字符
      String sortFieldParamName = "user.profile.updated_at";

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "PAGE_NUMBER",
              null,
              null,
              sortFieldParamName,
              null);

      // Then: 应该成功创建
      assertThat(paginationConfig.sortFieldParamName()).isEqualTo("user.profile.updated_at");
    }

    @Test
    @DisplayName("应该处理 sortFieldParamName 为极短字符串的情况")
    void shouldHandleMinimalSortFieldParamName() {
      // Given: sortFieldParamName 为单字符
      String sortFieldParamName = "t";

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "PAGE_NUMBER",
              null,
              null,
              sortFieldParamName,
              null);

      // Then: 应该成功创建
      assertThat(paginationConfig.sortFieldParamName()).isEqualTo("t");
    }

    @Test
    @DisplayName("应该处理 sortingDirection 为负数的情况")
    void shouldHandleNegativeSortingDirection() {
      // Given: sortingDirection 为负数
      Integer sortingDirection = -1;

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "PAGE_NUMBER",
              null,
              null,
              "updated_at",
              sortingDirection);

      // Then: 应该成功创建
      assertThat(paginationConfig.sortingDirection()).isEqualTo(-1);
    }

    @Test
    @DisplayName("应该处理 sortingDirection 为大于 1 的情况")
    void shouldHandleSortingDirectionGreaterThanOne() {
      // Given: sortingDirection 为大于 1
      Integer sortingDirection = 2;

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "PAGE_NUMBER",
              null,
              null,
              "updated_at",
              sortingDirection);

      // Then: 应该成功创建
      assertThat(paginationConfig.sortingDirection()).isEqualTo(2);
    }

    @Test
    @DisplayName("应该处理 pageSizeValue 为 0 的情况")
    void shouldHandleZeroPageSize() {
      // Given: pageSizeValue 为 0
      Integer pageSizeValue = 0;

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "PAGE_NUMBER",
              pageSizeValue,
              null,
              null,
              null);

      // Then: 应该成功创建
      assertThat(paginationConfig.pageSizeValue()).isEqualTo(0);
    }

    @Test
    @DisplayName("应该处理 maxPagesPerExecution 为 0 的情况")
    void shouldHandleZeroMaxPages() {
      // Given: maxPagesPerExecution 为 0
      Integer maxPagesPerExecution = 0;

      // When: 创建 PaginationConfig
      PaginationConfig paginationConfig =
          new PaginationConfig(
              1001L,
              2001L,
              "HARVEST",
              Instant.now(),
              null,
              "PAGE_NUMBER",
              null,
              maxPagesPerExecution,
              null,
              null);

      // Then: 应该成功创建
      assertThat(paginationConfig.maxPagesPerExecution()).isEqualTo(0);
    }
  }
}
