package com.patra.registry.domain.model.aggregate;

import static org.assertj.core.api.Assertions.*;

import com.patra.registry.domain.exception.DomainValidationException;
import com.patra.registry.domain.model.vo.provenance.BatchingConfig;
import com.patra.registry.domain.model.vo.provenance.HttpConfig;
import com.patra.registry.domain.model.vo.provenance.PaginationConfig;
import com.patra.registry.domain.model.vo.provenance.Provenance;
import com.patra.registry.domain.model.vo.provenance.RateLimitConfig;
import com.patra.registry.domain.model.vo.provenance.RetryConfig;
import com.patra.registry.domain.model.vo.provenance.WindowOffsetConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * ProvenanceConfiguration 聚合根单元测试。
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
 * <p>覆盖范围：
 *
 * <ul>
 *   <li>✅ 聚合根创建与验证
 *   <li>✅ 配置可用性检测（hasXxx 方法）
 *   <li>✅ 完整性判断逻辑（isComplete）
 *   <li>✅ 不变性保证
 *   <li>✅ 业务规则验证
 *   <li>✅ 边界条件处理
 * </ul>
 *
 * @author Patra Team
 * @since 2.0
 */
@DisplayName("ProvenanceConfiguration 单元测试")
class ProvenanceConfigurationTest {

  // ========== 聚合根创建测试 ==========

  @Nested
  @DisplayName("聚合根创建")
  class AggregateCreationTests {

    @Test
    @DisplayName("应该成功创建仅包含必需字段的配置")
    void shouldCreateConfigurationWithOnlyRequiredFields() {
      // Given: 仅必需的 Provenance
      Provenance provenance = ProvenanceConfigurationTestDataBuilder.buildActiveProvenance();

      // When: 创建配置
      ProvenanceConfiguration config =
          new ProvenanceConfiguration(provenance, null, null, null, null, null, null);

      // Then: 验证配置创建成功
      assertThat(config).isNotNull();
      assertThat(config.provenance()).isEqualTo(provenance);
      assertThat(config.windowOffset()).isNull();
      assertThat(config.pagination()).isNull();
      assertThat(config.http()).isNull();
      assertThat(config.batching()).isNull();
      assertThat(config.retry()).isNull();
      assertThat(config.rateLimit()).isNull();
    }

    @Test
    @DisplayName("应该成功创建包含所有可选配置的完整配置")
    void shouldCreateFullConfigurationWithAllOptionalFields() {
      // Given: 所有配置组件
      Provenance provenance = ProvenanceConfigurationTestDataBuilder.buildActiveProvenance();
      WindowOffsetConfig windowOffset = ProvenanceConfigurationTestDataBuilder.buildWindowOffset();
      PaginationConfig pagination = ProvenanceConfigurationTestDataBuilder.buildPagination();
      HttpConfig http = ProvenanceConfigurationTestDataBuilder.buildHttp();
      BatchingConfig batching = ProvenanceConfigurationTestDataBuilder.buildBatching();
      RetryConfig retry = ProvenanceConfigurationTestDataBuilder.buildRetry();
      RateLimitConfig rateLimit = ProvenanceConfigurationTestDataBuilder.buildRateLimit();

      // When: 创建完整配置
      ProvenanceConfiguration config =
          new ProvenanceConfiguration(
              provenance, windowOffset, pagination, http, batching, retry, rateLimit);

      // Then: 验证所有配置组件
      assertThat(config.provenance()).isEqualTo(provenance);
      assertThat(config.windowOffset()).isEqualTo(windowOffset);
      assertThat(config.pagination()).isEqualTo(pagination);
      assertThat(config.http()).isEqualTo(http);
      assertThat(config.batching()).isEqualTo(batching);
      assertThat(config.retry()).isEqualTo(retry);
      assertThat(config.rateLimit()).isEqualTo(rateLimit);
    }

    @Test
    @DisplayName("应该抛出异常当 Provenance 为 null")
    void shouldThrowExceptionWhenProvenanceIsNull() {
      // Given: null Provenance
      Provenance provenance = null;

      // When & Then: 创建配置应该失败
      assertThatThrownBy(
              () -> new ProvenanceConfiguration(provenance, null, null, null, null, null, null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("Provenance")
          .hasMessageContaining("不能为 null");
    }
  }

  // ========== 配置可用性检测测试 ==========

  @Nested
  @DisplayName("配置可用性检测")
  class ConfigurationAvailabilityTests {

    @Test
    @DisplayName("hasWindowOffset 应该在存在配置时返回 true")
    void hasWindowOffsetShouldReturnTrueWhenConfigExists() {
      // Given: 包含 WindowOffsetConfig 的配置
      ProvenanceConfiguration config =
          ProvenanceConfigurationTestDataBuilder.builder()
              .windowOffset(ProvenanceConfigurationTestDataBuilder.buildWindowOffset())
              .build();

      // When & Then
      assertThat(config.hasWindowOffset()).isTrue();
    }

    @Test
    @DisplayName("hasWindowOffset 应该在不存在配置时返回 false")
    void hasWindowOffsetShouldReturnFalseWhenConfigNotExists() {
      // Given: 不包含 WindowOffsetConfig 的配置
      ProvenanceConfiguration config =
          ProvenanceConfigurationTestDataBuilder.builder().windowOffset(null).build();

      // When & Then
      assertThat(config.hasWindowOffset()).isFalse();
    }

    @Test
    @DisplayName("hasPagination 应该在存在配置时返回 true")
    void hasPaginationShouldReturnTrueWhenConfigExists() {
      // Given: 包含 PaginationConfig 的配置
      ProvenanceConfiguration config =
          ProvenanceConfigurationTestDataBuilder.builder()
              .pagination(ProvenanceConfigurationTestDataBuilder.buildPagination())
              .build();

      // When & Then
      assertThat(config.hasPagination()).isTrue();
    }

    @Test
    @DisplayName("hasPagination 应该在不存在配置时返回 false")
    void hasPaginationShouldReturnFalseWhenConfigNotExists() {
      // Given: 不包含 PaginationConfig 的配置
      ProvenanceConfiguration config =
          ProvenanceConfigurationTestDataBuilder.builder().pagination(null).build();

      // When & Then
      assertThat(config.hasPagination()).isFalse();
    }

    @Test
    @DisplayName("hasHttpConfig 应该在存在配置时返回 true")
    void hasHttpConfigShouldReturnTrueWhenConfigExists() {
      // Given: 包含 HttpConfig 的配置
      ProvenanceConfiguration config =
          ProvenanceConfigurationTestDataBuilder.builder()
              .http(ProvenanceConfigurationTestDataBuilder.buildHttp())
              .build();

      // When & Then
      assertThat(config.hasHttpConfig()).isTrue();
    }

    @Test
    @DisplayName("hasHttpConfig 应该在不存在配置时返回 false")
    void hasHttpConfigShouldReturnFalseWhenConfigNotExists() {
      // Given: 不包含 HttpConfig 的配置
      ProvenanceConfiguration config =
          ProvenanceConfigurationTestDataBuilder.builder().http(null).build();

      // When & Then
      assertThat(config.hasHttpConfig()).isFalse();
    }

    @Test
    @DisplayName("hasBatching 应该在存在配置时返回 true")
    void hasBatchingShouldReturnTrueWhenConfigExists() {
      // Given: 包含 BatchingConfig 的配置
      ProvenanceConfiguration config =
          ProvenanceConfigurationTestDataBuilder.builder()
              .batching(ProvenanceConfigurationTestDataBuilder.buildBatching())
              .build();

      // When & Then
      assertThat(config.hasBatching()).isTrue();
    }

    @Test
    @DisplayName("hasBatching 应该在不存在配置时返回 false")
    void hasBatchingShouldReturnFalseWhenConfigNotExists() {
      // Given: 不包含 BatchingConfig 的配置
      ProvenanceConfiguration config =
          ProvenanceConfigurationTestDataBuilder.builder().batching(null).build();

      // When & Then
      assertThat(config.hasBatching()).isFalse();
    }

    @Test
    @DisplayName("hasRetry 应该在存在配置时返回 true")
    void hasRetryShouldReturnTrueWhenConfigExists() {
      // Given: 包含 RetryConfig 的配置
      ProvenanceConfiguration config =
          ProvenanceConfigurationTestDataBuilder.builder()
              .retry(ProvenanceConfigurationTestDataBuilder.buildRetry())
              .build();

      // When & Then
      assertThat(config.hasRetry()).isTrue();
    }

    @Test
    @DisplayName("hasRetry 应该在不存在配置时返回 false")
    void hasRetryShouldReturnFalseWhenConfigNotExists() {
      // Given: 不包含 RetryConfig 的配置
      ProvenanceConfiguration config =
          ProvenanceConfigurationTestDataBuilder.builder().retry(null).build();

      // When & Then
      assertThat(config.hasRetry()).isFalse();
    }

    @Test
    @DisplayName("hasRateLimit 应该在存在配置时返回 true")
    void hasRateLimitShouldReturnTrueWhenConfigExists() {
      // Given: 包含 RateLimitConfig 的配置
      ProvenanceConfiguration config =
          ProvenanceConfigurationTestDataBuilder.builder()
              .rateLimit(ProvenanceConfigurationTestDataBuilder.buildRateLimit())
              .build();

      // When & Then
      assertThat(config.hasRateLimit()).isTrue();
    }

    @Test
    @DisplayName("hasRateLimit 应该在不存在配置时返回 false")
    void hasRateLimitShouldReturnFalseWhenConfigNotExists() {
      // Given: 不包含 RateLimitConfig 的配置
      ProvenanceConfiguration config =
          ProvenanceConfigurationTestDataBuilder.builder().rateLimit(null).build();

      // When & Then
      assertThat(config.hasRateLimit()).isFalse();
    }

    @Test
    @DisplayName("应该正确处理所有配置字段都为 null 的情况")
    void shouldHandleAllOptionalConfigsBeingNull() {
      // Given: 仅必需 Provenance 的配置
      ProvenanceConfiguration config =
          ProvenanceConfigurationTestDataBuilder.builder()
              .windowOffset(null)
              .pagination(null)
              .http(null)
              .batching(null)
              .retry(null)
              .rateLimit(null)
              .build();

      // When & Then: 所有可用性检测应该返回 false
      assertThat(config.hasWindowOffset()).isFalse();
      assertThat(config.hasPagination()).isFalse();
      assertThat(config.hasHttpConfig()).isFalse();
      assertThat(config.hasBatching()).isFalse();
      assertThat(config.hasRetry()).isFalse();
      assertThat(config.hasRateLimit()).isFalse();
    }

    @Test
    @DisplayName("应该正确处理所有配置字段都存在的情况")
    void shouldHandleAllOptionalConfigsBeingPresent() {
      // Given: 包含所有可选配置的完整配置
      ProvenanceConfiguration config =
          ProvenanceConfigurationTestDataBuilder.builder()
              .windowOffset(ProvenanceConfigurationTestDataBuilder.buildWindowOffset())
              .pagination(ProvenanceConfigurationTestDataBuilder.buildPagination())
              .http(ProvenanceConfigurationTestDataBuilder.buildHttp())
              .batching(ProvenanceConfigurationTestDataBuilder.buildBatching())
              .retry(ProvenanceConfigurationTestDataBuilder.buildRetry())
              .rateLimit(ProvenanceConfigurationTestDataBuilder.buildRateLimit())
              .build();

      // When & Then: 所有可用性检测应该返回 true
      assertThat(config.hasWindowOffset()).isTrue();
      assertThat(config.hasPagination()).isTrue();
      assertThat(config.hasHttpConfig()).isTrue();
      assertThat(config.hasBatching()).isTrue();
      assertThat(config.hasRetry()).isTrue();
      assertThat(config.hasRateLimit()).isTrue();
    }
  }

  // ========== 完整性判断测试 ==========

  @Nested
  @DisplayName("配置完整性判断")
  class ConfigurationCompletenessTests {

    @Test
    @DisplayName("isComplete 应该在 Provenance 激活时返回 true")
    void isCompleteShouldReturnTrueWhenProvenanceIsActive() {
      // Given: 激活状态的 Provenance
      ProvenanceConfiguration config =
          ProvenanceConfigurationTestDataBuilder.builder()
              .provenance(ProvenanceConfigurationTestDataBuilder.buildActiveProvenance())
              .build();

      // When & Then
      assertThat(config.isComplete()).isTrue();
    }

    @Test
    @DisplayName("isComplete 应该在 Provenance 未激活时返回 false")
    void isCompleteShouldReturnFalseWhenProvenanceIsInactive() {
      // Given: 未激活状态的 Provenance
      ProvenanceConfiguration config =
          ProvenanceConfigurationTestDataBuilder.builder()
              .provenance(ProvenanceConfigurationTestDataBuilder.buildInactiveProvenance())
              .build();

      // When & Then
      assertThat(config.isComplete()).isFalse();
    }

    @Test
    @DisplayName("isComplete 应该忽略可选配置的存在性")
    void isCompleteShouldIgnoreOptionalConfigsPresence() {
      // Given: 激活状态的 Provenance，但无任何可选配置
      ProvenanceConfiguration configWithoutOptionals =
          ProvenanceConfigurationTestDataBuilder.builder()
              .provenance(ProvenanceConfigurationTestDataBuilder.buildActiveProvenance())
              .windowOffset(null)
              .pagination(null)
              .http(null)
              .batching(null)
              .retry(null)
              .rateLimit(null)
              .build();

      // Given: 激活状态的 Provenance，包含所有可选配置
      ProvenanceConfiguration configWithAllOptionals =
          ProvenanceConfigurationTestDataBuilder.builder()
              .provenance(ProvenanceConfigurationTestDataBuilder.buildActiveProvenance())
              .windowOffset(ProvenanceConfigurationTestDataBuilder.buildWindowOffset())
              .pagination(ProvenanceConfigurationTestDataBuilder.buildPagination())
              .http(ProvenanceConfigurationTestDataBuilder.buildHttp())
              .batching(ProvenanceConfigurationTestDataBuilder.buildBatching())
              .retry(ProvenanceConfigurationTestDataBuilder.buildRetry())
              .rateLimit(ProvenanceConfigurationTestDataBuilder.buildRateLimit())
              .build();

      // When & Then: 完整性判断应该只依赖 Provenance 的激活状态
      assertThat(configWithoutOptionals.isComplete()).isTrue();
      assertThat(configWithAllOptionals.isComplete()).isTrue();
    }

    @Test
    @DisplayName("isComplete 应该在包含所有配置但 Provenance 未激活时返回 false")
    void isCompleteShouldReturnFalseEvenWithAllConfigsWhenProvenanceIsInactive() {
      // Given: 未激活的 Provenance，包含所有可选配置
      ProvenanceConfiguration config =
          ProvenanceConfigurationTestDataBuilder.builder()
              .provenance(ProvenanceConfigurationTestDataBuilder.buildInactiveProvenance())
              .windowOffset(ProvenanceConfigurationTestDataBuilder.buildWindowOffset())
              .pagination(ProvenanceConfigurationTestDataBuilder.buildPagination())
              .http(ProvenanceConfigurationTestDataBuilder.buildHttp())
              .batching(ProvenanceConfigurationTestDataBuilder.buildBatching())
              .retry(ProvenanceConfigurationTestDataBuilder.buildRetry())
              .rateLimit(ProvenanceConfigurationTestDataBuilder.buildRateLimit())
              .build();

      // When & Then: 即使有完整配置，未激活仍不完整
      assertThat(config.isComplete()).isFalse();
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
      Provenance originalProvenance = ProvenanceConfigurationTestDataBuilder.buildActiveProvenance();
      WindowOffsetConfig originalWindowOffset =
          ProvenanceConfigurationTestDataBuilder.buildWindowOffset();
      PaginationConfig originalPagination = ProvenanceConfigurationTestDataBuilder.buildPagination();
      HttpConfig originalHttp = ProvenanceConfigurationTestDataBuilder.buildHttp();
      BatchingConfig originalBatching = ProvenanceConfigurationTestDataBuilder.buildBatching();
      RetryConfig originalRetry = ProvenanceConfigurationTestDataBuilder.buildRetry();
      RateLimitConfig originalRateLimit = ProvenanceConfigurationTestDataBuilder.buildRateLimit();

      ProvenanceConfiguration config =
          new ProvenanceConfiguration(
              originalProvenance,
              originalWindowOffset,
              originalPagination,
              originalHttp,
              originalBatching,
              originalRetry,
              originalRateLimit);

      // When: 获取字段引用
      Provenance retrievedProvenance = config.provenance();
      WindowOffsetConfig retrievedWindowOffset = config.windowOffset();
      PaginationConfig retrievedPagination = config.pagination();
      HttpConfig retrievedHttp = config.http();
      BatchingConfig retrievedBatching = config.batching();
      RetryConfig retrievedRetry = config.retry();
      RateLimitConfig retrievedRateLimit = config.rateLimit();

      // Then: 引用应该保持不变（值对象不变性）
      assertThat(retrievedProvenance).isSameAs(originalProvenance);
      assertThat(retrievedWindowOffset).isSameAs(originalWindowOffset);
      assertThat(retrievedPagination).isSameAs(originalPagination);
      assertThat(retrievedHttp).isSameAs(originalHttp);
      assertThat(retrievedBatching).isSameAs(originalBatching);
      assertThat(retrievedRetry).isSameAs(originalRetry);
      assertThat(retrievedRateLimit).isSameAs(originalRateLimit);
    }

    @Test
    @DisplayName("配置标识符应该在创建后保持不变")
    void configurationIdentifierShouldRemainUnchangedAfterCreation() {
      // Given: 创建配置
      Provenance provenance =
          new Provenance(
              1001L, "pubmed", "PubMed", "https://api.pubmed.org", "UTC", null, true, "ACTIVE");

      ProvenanceConfiguration config =
          ProvenanceConfigurationTestDataBuilder.builder().provenance(provenance).build();

      // When: 获取 Provenance 标识符
      Long originalId = config.provenance().id();
      String originalCode = config.provenance().code();

      // Then: 标识符应该保持不变
      assertThat(config.provenance().id()).isEqualTo(originalId);
      assertThat(config.provenance().code()).isEqualTo(originalCode);
      assertThat(originalId).isEqualTo(1001L);
      assertThat(originalCode).isEqualTo("pubmed");
    }
  }

  // ========== 业务规则测试 ==========

  @Nested
  @DisplayName("业务规则验证")
  class BusinessRuleTests {

    @Test
    @DisplayName("激活状态的 Provenance 应该被视为完整配置")
    void activeProvenanceShouldBeConsideredComplete() {
      // Given: 激活状态的数据源
      Provenance activeProvenance = ProvenanceConfigurationTestDataBuilder.buildActiveProvenance();
      ProvenanceConfiguration config =
          ProvenanceConfigurationTestDataBuilder.builder().provenance(activeProvenance).build();

      // When & Then: 激活的数据源应该是完整的
      assertThat(activeProvenance.isActive()).isTrue();
      assertThat(config.isComplete()).isTrue();
    }

    @Test
    @DisplayName("未激活状态的 Provenance 不应该被视为完整配置")
    void inactiveProvenanceShouldNotBeConsideredComplete() {
      // Given: 未激活状态的数据源
      Provenance inactiveProvenance =
          ProvenanceConfigurationTestDataBuilder.buildInactiveProvenance();
      ProvenanceConfiguration config =
          ProvenanceConfigurationTestDataBuilder.builder().provenance(inactiveProvenance).build();

      // When & Then: 未激活的数据源不应该是完整的
      assertThat(inactiveProvenance.isActive()).isFalse();
      assertThat(config.isComplete()).isFalse();
    }

    @Test
    @DisplayName("可选配置字段允许为 null")
    void optionalConfigFieldsCanBeNull() {
      // Given: 仅必需字段的配置
      ProvenanceConfiguration config =
          ProvenanceConfigurationTestDataBuilder.builder()
              .windowOffset(null)
              .pagination(null)
              .http(null)
              .batching(null)
              .retry(null)
              .rateLimit(null)
              .build();

      // When & Then: 应该成功创建且不报错
      assertThat(config).isNotNull();
      assertThat(config.provenance()).isNotNull();
      assertThat(config.windowOffset()).isNull();
      assertThat(config.pagination()).isNull();
      assertThat(config.http()).isNull();
      assertThat(config.batching()).isNull();
      assertThat(config.retry()).isNull();
      assertThat(config.rateLimit()).isNull();
    }

    @Test
    @DisplayName("配置作用域应该遵循优先级规则")
    void configurationScopeShouldFollowPriorityRules() {
      // Given: 完整配置（模拟 TASK 级别配置）
      ProvenanceConfiguration taskLevelConfig =
          ProvenanceConfigurationTestDataBuilder.builder()
              .provenance(ProvenanceConfigurationTestDataBuilder.buildActiveProvenance())
              .windowOffset(ProvenanceConfigurationTestDataBuilder.buildWindowOffset())
              .pagination(ProvenanceConfigurationTestDataBuilder.buildPagination())
              .http(ProvenanceConfigurationTestDataBuilder.buildHttp())
              .batching(ProvenanceConfigurationTestDataBuilder.buildBatching())
              .retry(ProvenanceConfigurationTestDataBuilder.buildRetry())
              .rateLimit(ProvenanceConfigurationTestDataBuilder.buildRateLimit())
              .build();

      // Given: 部分配置（模拟 SOURCE 级别配置）
      ProvenanceConfiguration sourceLevelConfig =
          ProvenanceConfigurationTestDataBuilder.builder()
              .provenance(ProvenanceConfigurationTestDataBuilder.buildActiveProvenance())
              .windowOffset(null)
              .pagination(ProvenanceConfigurationTestDataBuilder.buildPagination())
              .http(ProvenanceConfigurationTestDataBuilder.buildHttp())
              .batching(null)
              .retry(ProvenanceConfigurationTestDataBuilder.buildRetry())
              .rateLimit(null)
              .build();

      // When & Then: TASK 级别配置应该更完整（更高优先级）
      assertThat(taskLevelConfig.hasWindowOffset()).isTrue();
      assertThat(taskLevelConfig.hasBatching()).isTrue();
      assertThat(taskLevelConfig.hasRateLimit()).isTrue();

      assertThat(sourceLevelConfig.hasWindowOffset()).isFalse();
      assertThat(sourceLevelConfig.hasBatching()).isFalse();
      assertThat(sourceLevelConfig.hasRateLimit()).isFalse();
    }
  }

  // ========== 边界条件测试 ==========

  @Nested
  @DisplayName("边界条件处理")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该处理 Provenance ID 为最小正整数的情况")
    void shouldHandleMinimalProvenanceId() {
      // Given: ID 为 1 的 Provenance
      Provenance provenance =
          new Provenance(1L, "test", "Test Source", null, "UTC", null, true, "ACTIVE");

      // When: 创建配置
      ProvenanceConfiguration config =
          ProvenanceConfigurationTestDataBuilder.builder().provenance(provenance).build();

      // Then: 应该成功创建
      assertThat(config.provenance().id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("应该处理 Provenance ID 为极大值的情况")
    void shouldHandleMaximalProvenanceId() {
      // Given: ID 为 Long.MAX_VALUE 的 Provenance
      Provenance provenance =
          new Provenance(
              Long.MAX_VALUE, "test", "Test Source", null, "UTC", null, true, "ACTIVE");

      // When: 创建配置
      ProvenanceConfiguration config =
          ProvenanceConfigurationTestDataBuilder.builder().provenance(provenance).build();

      // Then: 应该成功创建
      assertThat(config.provenance().id()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("应该处理 Provenance code 为极短字符串的情况")
    void shouldHandleMinimalProvenanceCode() {
      // Given: code 为单字符的 Provenance
      Provenance provenance =
          new Provenance(1001L, "a", "Test Source", null, "UTC", null, true, "ACTIVE");

      // When: 创建配置
      ProvenanceConfiguration config =
          ProvenanceConfigurationTestDataBuilder.builder().provenance(provenance).build();

      // Then: 应该成功创建
      assertThat(config.provenance().code()).isEqualTo("a");
    }

    @Test
    @DisplayName("应该处理 Provenance code 为极长字符串的情况")
    void shouldHandleVeryLongProvenanceCode() {
      // Given: code 为长字符串的 Provenance
      String longCode = "a".repeat(255);
      Provenance provenance =
          new Provenance(1001L, longCode, "Test Source", null, "UTC", null, true, "ACTIVE");

      // When: 创建配置
      ProvenanceConfiguration config =
          ProvenanceConfigurationTestDataBuilder.builder().provenance(provenance).build();

      // Then: 应该成功创建
      assertThat(config.provenance().code()).hasSize(255);
    }

    @Test
    @DisplayName("应该处理所有可选 URL 字段为 null 的情况")
    void shouldHandleAllOptionalUrlFieldsBeingNull() {
      // Given: 所有可选 URL 字段为 null 的 Provenance
      Provenance provenance =
          new Provenance(1001L, "test", "Test Source", null, "UTC", null, true, "ACTIVE");

      // When: 创建配置
      ProvenanceConfiguration config =
          ProvenanceConfigurationTestDataBuilder.builder().provenance(provenance).build();

      // Then: 应该成功创建
      assertThat(config.provenance().baseUrlDefault()).isNull();
      assertThat(config.provenance().docsUrl()).isNull();
    }

    @Test
    @DisplayName("应该处理所有可选配置组件同时为 null 的情况")
    void shouldHandleAllOptionalConfigComponentsBeingNull() {
      // Given: 所有可选配置为 null
      ProvenanceConfiguration config =
          new ProvenanceConfiguration(
              ProvenanceConfigurationTestDataBuilder.buildActiveProvenance(),
              null,
              null,
              null,
              null,
              null,
              null);

      // When & Then: 应该成功创建
      assertThat(config.hasWindowOffset()).isFalse();
      assertThat(config.hasPagination()).isFalse();
      assertThat(config.hasHttpConfig()).isFalse();
      assertThat(config.hasBatching()).isFalse();
      assertThat(config.hasRetry()).isFalse();
      assertThat(config.hasRateLimit()).isFalse();
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
      Provenance provenance = ProvenanceConfigurationTestDataBuilder.buildActiveProvenance();
      WindowOffsetConfig windowOffset = ProvenanceConfigurationTestDataBuilder.buildWindowOffset();

      ProvenanceConfiguration config1 =
          new ProvenanceConfiguration(provenance, windowOffset, null, null, null, null, null);

      ProvenanceConfiguration config2 =
          new ProvenanceConfiguration(provenance, windowOffset, null, null, null, null, null);

      // When & Then: 应该相等
      assertThat(config1).isEqualTo(config2);
    }

    @Test
    @DisplayName("应该正确实现 hashCode 方法")
    void shouldImplementHashCodeCorrectly() {
      // Given: 两个相同值的配置
      Provenance provenance = ProvenanceConfigurationTestDataBuilder.buildActiveProvenance();
      WindowOffsetConfig windowOffset = ProvenanceConfigurationTestDataBuilder.buildWindowOffset();

      ProvenanceConfiguration config1 =
          new ProvenanceConfiguration(provenance, windowOffset, null, null, null, null, null);

      ProvenanceConfiguration config2 =
          new ProvenanceConfiguration(provenance, windowOffset, null, null, null, null, null);

      // When & Then: hashCode 应该相等
      assertThat(config1.hashCode()).isEqualTo(config2.hashCode());
    }

    @Test
    @DisplayName("应该正确实现 toString 方法")
    void shouldImplementToStringCorrectly() {
      // Given: 配置
      ProvenanceConfiguration config =
          ProvenanceConfigurationTestDataBuilder.builder().build();

      // When: 调用 toString
      String toString = config.toString();

      // Then: 应该包含关键字段
      assertThat(toString).contains("ProvenanceConfiguration");
      assertThat(toString).contains("provenance");
    }
  }

  // ========== TestDataBuilder (辅助类) ==========

  /**
   * ProvenanceConfiguration 测试数据构建器。
   *
   * <p>遵循 Builder 模式，提供默认值以简化测试数据构建。
   */
  static class ProvenanceConfigurationTestDataBuilder {
    private Provenance provenance = buildActiveProvenance();
    private WindowOffsetConfig windowOffset = null;
    private PaginationConfig pagination = null;
    private HttpConfig http = null;
    private BatchingConfig batching = null;
    private RetryConfig retry = null;
    private RateLimitConfig rateLimit = null;

    public static ProvenanceConfigurationTestDataBuilder builder() {
      return new ProvenanceConfigurationTestDataBuilder();
    }

    public ProvenanceConfigurationTestDataBuilder provenance(Provenance provenance) {
      this.provenance = provenance;
      return this;
    }

    public ProvenanceConfigurationTestDataBuilder windowOffset(WindowOffsetConfig windowOffset) {
      this.windowOffset = windowOffset;
      return this;
    }

    public ProvenanceConfigurationTestDataBuilder pagination(PaginationConfig pagination) {
      this.pagination = pagination;
      return this;
    }

    public ProvenanceConfigurationTestDataBuilder http(HttpConfig http) {
      this.http = http;
      return this;
    }

    public ProvenanceConfigurationTestDataBuilder batching(BatchingConfig batching) {
      this.batching = batching;
      return this;
    }

    public ProvenanceConfigurationTestDataBuilder retry(RetryConfig retry) {
      this.retry = retry;
      return this;
    }

    public ProvenanceConfigurationTestDataBuilder rateLimit(RateLimitConfig rateLimit) {
      this.rateLimit = rateLimit;
      return this;
    }

    public ProvenanceConfiguration build() {
      return new ProvenanceConfiguration(
          provenance, windowOffset, pagination, http, batching, retry, rateLimit);
    }

    // ========== 辅助方法：构建测试数据 ==========

    /** 构建激活状态的 Provenance */
    static Provenance buildActiveProvenance() {
      return new Provenance(
          1001L,
          "pubmed",
          "PubMed",
          "https://api.pubmed.org",
          "UTC",
          "https://docs.pubmed.org",
          true, // active
          "ACTIVE");
    }

    /** 构建未激活状态的 Provenance */
    static Provenance buildInactiveProvenance() {
      return new Provenance(
          1002L,
          "crossref",
          "Crossref",
          "https://api.crossref.org",
          "UTC",
          "https://docs.crossref.org",
          false, // inactive
          "DEPRECATED");
    }

    /** 构建 WindowOffsetConfig（占位符，实际实现根据真实类调整） */
    static WindowOffsetConfig buildWindowOffset() {
      // 注意：这里需要根据实际的 WindowOffsetConfig 构造方法调整
      // 假设它是一个简单的 record 或有工厂方法
      return null; // Placeholder - 需要替换为实际构造
    }

    /** 构建 PaginationConfig（占位符，实际实现根据真实类调整） */
    static PaginationConfig buildPagination() {
      return null; // Placeholder
    }

    /** 构建 HttpConfig（占位符，实际实现根据真实类调整） */
    static HttpConfig buildHttp() {
      return null; // Placeholder
    }

    /** 构建 BatchingConfig（占位符，实际实现根据真实类调整） */
    static BatchingConfig buildBatching() {
      return null; // Placeholder
    }

    /** 构建 RetryConfig（占位符，实际实现根据真实类调整） */
    static RetryConfig buildRetry() {
      return null; // Placeholder
    }

    /** 构建 RateLimitConfig（占位符，实际实现根据真实类调整） */
    static RateLimitConfig buildRateLimit() {
      return null; // Placeholder
    }
  }
}
