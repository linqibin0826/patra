package com.patra.registry.domain.model.read.provenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link BatchingConfigQuery} 的单元测试。
 *
 * <p>测试覆盖：
 *
 * <ul>
 *   <li>成功构造（完整字段、部分可选字段、最小必填字段）
 *   <li>验证失败（id、provenanceId、effectiveFrom 的各种无效值）
 *   <li>Trim 逻辑（operationType、idsParamName、idsJoinDelimiter）
 *   <li>Record 语义（equals、hashCode、toString、组件访问器）
 *   <li>不可变性验证
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("BatchingConfigQuery 单元测试")
class BatchingConfigQueryTest {

  // ========== 测试数据常量 ==========

  private static final Long VALID_ID = 1L;
  private static final Long VALID_PROVENANCE_ID = 100L;
  private static final String VALID_OPERATION_TYPE = "FETCH_DETAILS";
  private static final Instant VALID_EFFECTIVE_FROM = Instant.parse("2025-01-01T00:00:00Z");
  private static final Instant VALID_EFFECTIVE_TO = Instant.parse("2025-12-31T23:59:59Z");
  private static final Integer VALID_DETAIL_FETCH_BATCH_SIZE = 50;
  private static final String VALID_IDS_PARAM_NAME = "ids";
  private static final String VALID_IDS_JOIN_DELIMITER = ",";
  private static final Integer VALID_MAX_IDS_PER_REQUEST = 100;

  // ========== 成功构造测试 ==========

  @Nested
  @DisplayName("成功构造测试")
  class SuccessfulConstructionTests {

    @Test
    @DisplayName("应该成功构造：所有字段有效（包括可选字段）")
    void shouldConstructSuccessfully_whenAllFieldsValid() {
      // Given & When
      var query =
          new BatchingConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO,
              VALID_DETAIL_FETCH_BATCH_SIZE,
              VALID_IDS_PARAM_NAME,
              VALID_IDS_JOIN_DELIMITER,
              VALID_MAX_IDS_PER_REQUEST);

      // Then
      assertThat(query).isNotNull();
      assertThat(query.id()).isEqualTo(VALID_ID);
      assertThat(query.provenanceId()).isEqualTo(VALID_PROVENANCE_ID);
      assertThat(query.operationType()).isEqualTo(VALID_OPERATION_TYPE);
      assertThat(query.effectiveFrom()).isEqualTo(VALID_EFFECTIVE_FROM);
      assertThat(query.effectiveTo()).isEqualTo(VALID_EFFECTIVE_TO);
      assertThat(query.detailFetchBatchSize()).isEqualTo(VALID_DETAIL_FETCH_BATCH_SIZE);
      assertThat(query.idsParamName()).isEqualTo(VALID_IDS_PARAM_NAME);
      assertThat(query.idsJoinDelimiter()).isEqualTo(VALID_IDS_JOIN_DELIMITER);
      assertThat(query.maxIdsPerRequest()).isEqualTo(VALID_MAX_IDS_PER_REQUEST);
    }

    @Test
    @DisplayName("应该成功构造：所有可选字段为 null")
    void shouldConstructSuccessfully_whenOptionalFieldsNull() {
      // Given & When
      var query =
          new BatchingConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null, // operationType
              VALID_EFFECTIVE_FROM,
              null, // effectiveTo
              null, // detailFetchBatchSize
              null, // idsParamName
              null, // idsJoinDelimiter
              null // maxIdsPerRequest
              );

      // Then
      assertThat(query).isNotNull();
      assertThat(query.id()).isEqualTo(VALID_ID);
      assertThat(query.provenanceId()).isEqualTo(VALID_PROVENANCE_ID);
      assertThat(query.operationType()).isNull();
      assertThat(query.effectiveFrom()).isEqualTo(VALID_EFFECTIVE_FROM);
      assertThat(query.effectiveTo()).isNull();
      assertThat(query.detailFetchBatchSize()).isNull();
      assertThat(query.idsParamName()).isNull();
      assertThat(query.idsJoinDelimiter()).isNull();
      assertThat(query.maxIdsPerRequest()).isNull();
    }

    @Test
    @DisplayName("应该成功构造：仅包含最小必填字段")
    void shouldConstructSuccessfully_whenOnlyRequiredFields() {
      // Given & When
      var query =
          new BatchingConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null,
              VALID_EFFECTIVE_FROM,
              null,
              null,
              null,
              null,
              null);

      // Then
      assertThat(query).isNotNull();
      assertThat(query.id()).isEqualTo(VALID_ID);
      assertThat(query.provenanceId()).isEqualTo(VALID_PROVENANCE_ID);
      assertThat(query.effectiveFrom()).isEqualTo(VALID_EFFECTIVE_FROM);
    }

    @Test
    @DisplayName("应该成功构造：id 为最小有效值 1")
    void shouldConstructSuccessfully_whenIdIsOne() {
      // Given & When
      var query =
          new BatchingConfigQuery(
              1L, VALID_PROVENANCE_ID, null, VALID_EFFECTIVE_FROM, null, null, null, null, null);

      // Then
      assertThat(query.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("应该成功构造：provenanceId 为最小有效值 1")
    void shouldConstructSuccessfully_whenProvenanceIdIsOne() {
      // Given & When
      var query =
          new BatchingConfigQuery(
              VALID_ID, 1L, null, VALID_EFFECTIVE_FROM, null, null, null, null, null);

      // Then
      assertThat(query.provenanceId()).isEqualTo(1L);
    }
  }

  // ========== 验证失败测试 ==========

  @Nested
  @DisplayName("验证失败测试")
  class ValidationFailureTests {

    @Nested
    @DisplayName("id 验证")
    class IdValidationTests {

      @Test
      @DisplayName("应该抛出异常：id 为 null")
      void shouldThrowException_whenIdIsNull() {
        // Given & When & Then
        assertThatThrownBy(
                () ->
                    new BatchingConfigQuery(
                        null,
                        VALID_PROVENANCE_ID,
                        null,
                        VALID_EFFECTIVE_FROM,
                        null,
                        null,
                        null,
                        null,
                        null))
            .isInstanceOf(DomainValidationException.class)
            .hasMessage("批处理配置ID必须为正数");
      }

      @Test
      @DisplayName("应该抛出异常：id 为 0")
      void shouldThrowException_whenIdIsZero() {
        // Given & When & Then
        assertThatThrownBy(
                () ->
                    new BatchingConfigQuery(
                        0L,
                        VALID_PROVENANCE_ID,
                        null,
                        VALID_EFFECTIVE_FROM,
                        null,
                        null,
                        null,
                        null,
                        null))
            .isInstanceOf(DomainValidationException.class)
            .hasMessage("批处理配置ID必须为正数");
      }

      @Test
      @DisplayName("应该抛出异常：id 为负数")
      void shouldThrowException_whenIdIsNegative() {
        // Given & When & Then
        assertThatThrownBy(
                () ->
                    new BatchingConfigQuery(
                        -1L,
                        VALID_PROVENANCE_ID,
                        null,
                        VALID_EFFECTIVE_FROM,
                        null,
                        null,
                        null,
                        null,
                        null))
            .isInstanceOf(DomainValidationException.class)
            .hasMessage("批处理配置ID必须为正数");
      }
    }

    @Nested
    @DisplayName("provenanceId 验证")
    class ProvenanceIdValidationTests {

      @Test
      @DisplayName("应该抛出异常：provenanceId 为 null")
      void shouldThrowException_whenProvenanceIdIsNull() {
        // Given & When & Then
        assertThatThrownBy(
                () ->
                    new BatchingConfigQuery(
                        VALID_ID, null, null, VALID_EFFECTIVE_FROM, null, null, null, null, null))
            .isInstanceOf(DomainValidationException.class)
            .hasMessage("来源ID必须为正数");
      }

      @Test
      @DisplayName("应该抛出异常：provenanceId 为 0")
      void shouldThrowException_whenProvenanceIdIsZero() {
        // Given & When & Then
        assertThatThrownBy(
                () ->
                    new BatchingConfigQuery(
                        VALID_ID, 0L, null, VALID_EFFECTIVE_FROM, null, null, null, null, null))
            .isInstanceOf(DomainValidationException.class)
            .hasMessage("来源ID必须为正数");
      }

      @Test
      @DisplayName("应该抛出异常：provenanceId 为负数")
      void shouldThrowException_whenProvenanceIdIsNegative() {
        // Given & When & Then
        assertThatThrownBy(
                () ->
                    new BatchingConfigQuery(
                        VALID_ID, -1L, null, VALID_EFFECTIVE_FROM, null, null, null, null, null))
            .isInstanceOf(DomainValidationException.class)
            .hasMessage("来源ID必须为正数");
      }
    }

    @Nested
    @DisplayName("effectiveFrom 验证")
    class EffectiveFromValidationTests {

      @Test
      @DisplayName("应该抛出异常：effectiveFrom 为 null")
      void shouldThrowException_whenEffectiveFromIsNull() {
        // Given & When & Then
        assertThatThrownBy(
                () ->
                    new BatchingConfigQuery(
                        VALID_ID, VALID_PROVENANCE_ID, null, null, null, null, null, null, null))
            .isInstanceOf(DomainValidationException.class)
            .hasMessage("生效时间不能为null");
      }
    }
  }

  // ========== Trim 逻辑测试 ==========

  @Nested
  @DisplayName("Trim 逻辑测试")
  class TrimLogicTests {

    @Test
    @DisplayName("应该 trim operationType 的前后空格")
    void shouldTrimOperationType() {
      // Given
      String operationTypeWithSpaces = "  FETCH_DETAILS  ";

      // When
      var query =
          new BatchingConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              operationTypeWithSpaces,
              VALID_EFFECTIVE_FROM,
              null,
              null,
              null,
              null,
              null);

      // Then
      assertThat(query.operationType()).isEqualTo("FETCH_DETAILS");
    }

    @Test
    @DisplayName("应该保留 operationType 为 null")
    void shouldKeepOperationTypeNull() {
      // Given & When
      var query =
          new BatchingConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null,
              VALID_EFFECTIVE_FROM,
              null,
              null,
              null,
              null,
              null);

      // Then
      assertThat(query.operationType()).isNull();
    }

    @Test
    @DisplayName("应该 trim idsParamName 的前后空格")
    void shouldTrimIdsParamName() {
      // Given
      String idsParamNameWithSpaces = "  ids  ";

      // When
      var query =
          new BatchingConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null,
              VALID_EFFECTIVE_FROM,
              null,
              null,
              idsParamNameWithSpaces,
              null,
              null);

      // Then
      assertThat(query.idsParamName()).isEqualTo("ids");
    }

    @Test
    @DisplayName("应该保留 idsParamName 为 null")
    void shouldKeepIdsParamNameNull() {
      // Given & When
      var query =
          new BatchingConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null,
              VALID_EFFECTIVE_FROM,
              null,
              null,
              null,
              null,
              null);

      // Then
      assertThat(query.idsParamName()).isNull();
    }

    @Test
    @DisplayName("应该 trim idsJoinDelimiter 的前后空格")
    void shouldTrimIdsJoinDelimiter() {
      // Given
      String idsJoinDelimiterWithSpaces = "  ,  ";

      // When
      var query =
          new BatchingConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null,
              VALID_EFFECTIVE_FROM,
              null,
              null,
              null,
              idsJoinDelimiterWithSpaces,
              null);

      // Then
      assertThat(query.idsJoinDelimiter()).isEqualTo(",");
    }

    @Test
    @DisplayName("应该保留 idsJoinDelimiter 为 null")
    void shouldKeepIdsJoinDelimiterNull() {
      // Given & When
      var query =
          new BatchingConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              null,
              VALID_EFFECTIVE_FROM,
              null,
              null,
              null,
              null,
              null);

      // Then
      assertThat(query.idsJoinDelimiter()).isNull();
    }

    @Test
    @DisplayName("应该同时 trim 所有字符串字段")
    void shouldTrimAllStringFieldsSimultaneously() {
      // Given
      String operationTypeWithSpaces = "  FETCH  ";
      String idsParamNameWithSpaces = "  ids  ";
      String idsJoinDelimiterWithSpaces = "  |  ";

      // When
      var query =
          new BatchingConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              operationTypeWithSpaces,
              VALID_EFFECTIVE_FROM,
              null,
              null,
              idsParamNameWithSpaces,
              idsJoinDelimiterWithSpaces,
              null);

      // Then
      assertThat(query.operationType()).isEqualTo("FETCH");
      assertThat(query.idsParamName()).isEqualTo("ids");
      assertThat(query.idsJoinDelimiter()).isEqualTo("|");
    }
  }

  // ========== Record 语义测试 ==========

  @Nested
  @DisplayName("Record 语义测试")
  class RecordSemanticsTests {

    @Test
    @DisplayName("应该正确实现 equals：相同字段值的实例相等")
    void shouldImplementEquals_whenSameFieldValues() {
      // Given
      var query1 =
          new BatchingConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO,
              VALID_DETAIL_FETCH_BATCH_SIZE,
              VALID_IDS_PARAM_NAME,
              VALID_IDS_JOIN_DELIMITER,
              VALID_MAX_IDS_PER_REQUEST);

      var query2 =
          new BatchingConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO,
              VALID_DETAIL_FETCH_BATCH_SIZE,
              VALID_IDS_PARAM_NAME,
              VALID_IDS_JOIN_DELIMITER,
              VALID_MAX_IDS_PER_REQUEST);

      // When & Then
      assertThat(query1).isEqualTo(query2);
    }

    @Test
    @DisplayName("应该正确实现 equals：不同 id 的实例不相等")
    void shouldImplementEquals_whenDifferentId() {
      // Given
      var query1 =
          new BatchingConfigQuery(
              1L, VALID_PROVENANCE_ID, null, VALID_EFFECTIVE_FROM, null, null, null, null, null);
      var query2 =
          new BatchingConfigQuery(
              2L, VALID_PROVENANCE_ID, null, VALID_EFFECTIVE_FROM, null, null, null, null, null);

      // When & Then
      assertThat(query1).isNotEqualTo(query2);
    }

    @Test
    @DisplayName("应该正确实现 equals：不同 provenanceId 的实例不相等")
    void shouldImplementEquals_whenDifferentProvenanceId() {
      // Given
      var query1 =
          new BatchingConfigQuery(
              VALID_ID, 1L, null, VALID_EFFECTIVE_FROM, null, null, null, null, null);
      var query2 =
          new BatchingConfigQuery(
              VALID_ID, 2L, null, VALID_EFFECTIVE_FROM, null, null, null, null, null);

      // When & Then
      assertThat(query1).isNotEqualTo(query2);
    }

    @Test
    @DisplayName("应该正确实现 hashCode：相同字段值的实例产生相同 hashCode")
    void shouldImplementHashCode_whenSameFieldValues() {
      // Given
      var query1 =
          new BatchingConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO,
              VALID_DETAIL_FETCH_BATCH_SIZE,
              VALID_IDS_PARAM_NAME,
              VALID_IDS_JOIN_DELIMITER,
              VALID_MAX_IDS_PER_REQUEST);

      var query2 =
          new BatchingConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO,
              VALID_DETAIL_FETCH_BATCH_SIZE,
              VALID_IDS_PARAM_NAME,
              VALID_IDS_JOIN_DELIMITER,
              VALID_MAX_IDS_PER_REQUEST);

      // When & Then
      assertThat(query1.hashCode()).isEqualTo(query2.hashCode());
    }

    @Test
    @DisplayName("应该正确实现 toString：包含所有字段信息")
    void shouldImplementToString_containsAllFields() {
      // Given
      var query =
          new BatchingConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO,
              VALID_DETAIL_FETCH_BATCH_SIZE,
              VALID_IDS_PARAM_NAME,
              VALID_IDS_JOIN_DELIMITER,
              VALID_MAX_IDS_PER_REQUEST);

      // When
      String result = query.toString();

      // Then
      assertThat(result)
          .contains("BatchingConfigQuery")
          .contains("id=" + VALID_ID)
          .contains("provenanceId=" + VALID_PROVENANCE_ID)
          .contains("operationType=" + VALID_OPERATION_TYPE)
          .contains("effectiveFrom=" + VALID_EFFECTIVE_FROM)
          .contains("effectiveTo=" + VALID_EFFECTIVE_TO)
          .contains("detailFetchBatchSize=" + VALID_DETAIL_FETCH_BATCH_SIZE)
          .contains("idsParamName=" + VALID_IDS_PARAM_NAME)
          .contains("idsJoinDelimiter=" + VALID_IDS_JOIN_DELIMITER)
          .contains("maxIdsPerRequest=" + VALID_MAX_IDS_PER_REQUEST);
    }

    @Test
    @DisplayName("应该正确提供组件访问器：所有字段都可访问")
    void shouldProvideComponentAccessors() {
      // Given
      var query =
          new BatchingConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              VALID_EFFECTIVE_FROM,
              VALID_EFFECTIVE_TO,
              VALID_DETAIL_FETCH_BATCH_SIZE,
              VALID_IDS_PARAM_NAME,
              VALID_IDS_JOIN_DELIMITER,
              VALID_MAX_IDS_PER_REQUEST);

      // When & Then
      assertThat(query.id()).isEqualTo(VALID_ID);
      assertThat(query.provenanceId()).isEqualTo(VALID_PROVENANCE_ID);
      assertThat(query.operationType()).isEqualTo(VALID_OPERATION_TYPE);
      assertThat(query.effectiveFrom()).isEqualTo(VALID_EFFECTIVE_FROM);
      assertThat(query.effectiveTo()).isEqualTo(VALID_EFFECTIVE_TO);
      assertThat(query.detailFetchBatchSize()).isEqualTo(VALID_DETAIL_FETCH_BATCH_SIZE);
      assertThat(query.idsParamName()).isEqualTo(VALID_IDS_PARAM_NAME);
      assertThat(query.idsJoinDelimiter()).isEqualTo(VALID_IDS_JOIN_DELIMITER);
      assertThat(query.maxIdsPerRequest()).isEqualTo(VALID_MAX_IDS_PER_REQUEST);
    }
  }

  // ========== 不可变性测试 ==========

  @Nested
  @DisplayName("不可变性测试")
  class ImmutabilityTests {

    @Test
    @DisplayName("应该保证不可变性：Record 实例创建后字段不可修改")
    void shouldBeImmutable() {
      // Given
      Instant effectiveFrom = Instant.parse("2025-01-01T00:00:00Z");
      Instant effectiveTo = Instant.parse("2025-12-31T23:59:59Z");

      var query =
          new BatchingConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              VALID_OPERATION_TYPE,
              effectiveFrom,
              effectiveTo,
              VALID_DETAIL_FETCH_BATCH_SIZE,
              VALID_IDS_PARAM_NAME,
              VALID_IDS_JOIN_DELIMITER,
              VALID_MAX_IDS_PER_REQUEST);

      // When - 修改原始对象（Instant 是不可变的，但我们验证 record 的引用不变）
      Instant retrievedEffectiveFrom = query.effectiveFrom();
      Instant retrievedEffectiveTo = query.effectiveTo();

      // Then - 验证获取的对象与原始对象是相同引用
      assertThat(retrievedEffectiveFrom).isSameAs(effectiveFrom);
      assertThat(retrievedEffectiveTo).isSameAs(effectiveTo);

      // 验证 Record 的所有字段值不变
      assertThat(query.id()).isEqualTo(VALID_ID);
      assertThat(query.provenanceId()).isEqualTo(VALID_PROVENANCE_ID);
      assertThat(query.operationType()).isEqualTo(VALID_OPERATION_TYPE);
      assertThat(query.effectiveFrom()).isEqualTo(effectiveFrom);
      assertThat(query.effectiveTo()).isEqualTo(effectiveTo);
    }

    @Test
    @DisplayName("应该保证不可变性：Trim 后的字符串不受原始字符串影响")
    void shouldBeImmutable_afterTrim() {
      // Given
      String originalOperationType = "  FETCH_DETAILS  ";

      // When
      var query =
          new BatchingConfigQuery(
              VALID_ID,
              VALID_PROVENANCE_ID,
              originalOperationType,
              VALID_EFFECTIVE_FROM,
              null,
              null,
              null,
              null,
              null);

      // 修改原始字符串变量（实际上字符串是不可变的，这里只是重新赋值）
      originalOperationType = "MODIFIED";

      // Then - Record 中存储的是 trim 后的副本，不受影响
      assertThat(query.operationType()).isEqualTo("FETCH_DETAILS");
    }
  }
}
