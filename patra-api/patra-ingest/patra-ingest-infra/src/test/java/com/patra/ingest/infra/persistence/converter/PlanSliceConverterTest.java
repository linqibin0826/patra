package com.patra.ingest.infra.persistence.converter;

import static org.assertj.core.api.Assertions.*;

import com.patra.common.enums.ProvenanceCode;
import com.patra.common.json.JsonNodeMappings;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.enums.SliceStatus;
import com.patra.ingest.domain.model.vo.plan.PlanId;
import com.patra.ingest.domain.model.vo.slice.PlanSliceId;
import com.patra.ingest.infra.persistence.entity.PlanSliceDO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// PlanSliceConverter 单元测试。
///
/// 测试策略：
///
/// - 测试 toEntity() 转换的正确性（Aggregate → DO）
///   - 测试 toAggregate() 转换的正确性（DO → Aggregate）
///   - 测试所有 SliceStatus 枚举值的转换
///   - 测试 JSON 字段的转换（windowSpec, exprSnapshot）
///   - 测试 sliceNo 为 0 的情况（默认值）
///   - 测试双向转换一致性
///   - 测试 null 安全性
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("PlanSliceConverter 单元测试")
class PlanSliceConverterTest {

  private final PlanSliceConverter converter = new PlanSliceConverterImpl();

  private static final PlanId PLAN_ID = PlanId.of(2001L);
  private static final ProvenanceCode PROVENANCE_CODE = ProvenanceCode.PUBMED;
  private static final int SLICE_NO = 5;
  private static final String SLICE_SIGNATURE_HASH = "slice-hash-abc123";
  private static final String WINDOW_SPEC_JSON =
      "{\"strategy\":\"TIME\",\"window\":{\"from\":\"2025-01-01T00:00:00Z\",\"to\":\"2025-01-31T23:59:59Z\"}}";
  private static final String EXPR_HASH = "expr-hash-xyz789";
  private static final String EXPR_SNAPSHOT_JSON =
      "{\"type\":\"RANGE_QUERY\",\"field\":\"updated_at\"}";

  // ========== toEntity() 转换测试 ==========

  @Nested
  @DisplayName("toEntity() 转换测试")
  class ToEntityConversionTests {

    @Test
    @DisplayName("应该正确转换 PENDING 状态的 PlanSliceAggregate")
    void shouldConvertPendingStatusAggregateToEntity() {
      // Given: 创建 PENDING 状态的 PlanSliceAggregate
      PlanSliceAggregate aggregate =
          PlanSliceAggregate.restore(
              PlanSliceId.of(100L),
              PLAN_ID,
              PROVENANCE_CODE,
              SLICE_NO,
              SLICE_SIGNATURE_HASH,
              WINDOW_SPEC_JSON,
              EXPR_HASH,
              EXPR_SNAPSHOT_JSON,
              SliceStatus.PENDING,
              0L);

      // When: 转换为 DO
      PlanSliceDO result = converter.toEntity(aggregate);

      // Then: 验证基本字段
      assertThat(result).isNotNull();
      assertThat(result.getPlanId()).isEqualTo(PLAN_ID.value());
      assertThat(result.getProvenanceCode()).isEqualTo(PROVENANCE_CODE.getCode());
      assertThat(result.getSliceNo()).isEqualTo(SLICE_NO);
      assertThat(result.getSliceSignatureHash()).isEqualTo(SLICE_SIGNATURE_HASH);
      assertThat(result.getExprHash()).isEqualTo(EXPR_HASH);
      assertThat(result.getStatusCode()).isEqualTo("PENDING");

      // 验证 JSON 字段转换
      assertThat(result.getWindowSpec()).isNotNull();
      assertThat(JsonNodeMappings.jsonNodeToString(result.getWindowSpec()))
          .isEqualTo(WINDOW_SPEC_JSON);
      assertThat(result.getExprSnapshot()).isNotNull();
      assertThat(JsonNodeMappings.jsonNodeToString(result.getExprSnapshot()))
          .isEqualTo(EXPR_SNAPSHOT_JSON);
    }

    @Test
    @DisplayName("应该正确转换 ASSIGNED 状态的 PlanSliceAggregate")
    void shouldConvertAssignedStatusAggregate() {
      // Given: 创建 ASSIGNED 状态的 PlanSliceAggregate
      PlanSliceAggregate aggregate =
          PlanSliceAggregate.restore(
              PlanSliceId.of(200L),
              PLAN_ID,
              PROVENANCE_CODE,
              SLICE_NO,
              SLICE_SIGNATURE_HASH,
              WINDOW_SPEC_JSON,
              EXPR_HASH,
              EXPR_SNAPSHOT_JSON,
              SliceStatus.ASSIGNED,
              0L);

      // When: 转换为 DO
      PlanSliceDO result = converter.toEntity(aggregate);

      // Then: 验证状态
      assertThat(result.getStatusCode()).isEqualTo("ASSIGNED");
    }

    @Test
    @DisplayName("应该正确转换 FINISHED 状态的 PlanSliceAggregate")
    void shouldConvertFinishedStatusAggregate() {
      // Given: 创建 FINISHED 状态的 PlanSliceAggregate
      PlanSliceAggregate aggregate =
          PlanSliceAggregate.restore(
              PlanSliceId.of(300L),
              PLAN_ID,
              PROVENANCE_CODE,
              SLICE_NO,
              SLICE_SIGNATURE_HASH,
              WINDOW_SPEC_JSON,
              EXPR_HASH,
              EXPR_SNAPSHOT_JSON,
              SliceStatus.FINISHED,
              0L);

      // When: 转换为 DO
      PlanSliceDO result = converter.toEntity(aggregate);

      // Then: 验证状态
      assertThat(result.getStatusCode()).isEqualTo("FINISHED");
    }

    @Test
    @DisplayName("应该正确转换 sliceNo 为 0 的 PlanSliceAggregate")
    void shouldConvertAggregateWithZeroSliceNo() {
      // Given: 创建 sliceNo 为 0 的 PlanSliceAggregate
      PlanSliceAggregate aggregate =
          PlanSliceAggregate.restore(
              PlanSliceId.of(400L),
              PLAN_ID,
              PROVENANCE_CODE,
              0, // sliceNo = 0
              SLICE_SIGNATURE_HASH,
              WINDOW_SPEC_JSON,
              EXPR_HASH,
              EXPR_SNAPSHOT_JSON,
              SliceStatus.PENDING,
              0L);

      // When: 转换为 DO
      PlanSliceDO result = converter.toEntity(aggregate);

      // Then: 验证 sliceNo
      assertThat(result.getSliceNo()).isEqualTo(0);
    }

    @Test
    @DisplayName("应该正确转换所有 SliceStatus 枚举值")
    void shouldConvertAllSliceStatusValues() {
      // PENDING
      PlanSliceAggregate pending =
          PlanSliceAggregate.restore(
              PlanSliceId.of(1L),
              PLAN_ID,
              PROVENANCE_CODE,
              SLICE_NO,
              SLICE_SIGNATURE_HASH,
              WINDOW_SPEC_JSON,
              EXPR_HASH,
              EXPR_SNAPSHOT_JSON,
              SliceStatus.PENDING,
              0L);
      assertThat(converter.toEntity(pending).getStatusCode()).isEqualTo("PENDING");

      // ASSIGNED
      PlanSliceAggregate assigned =
          PlanSliceAggregate.restore(
              PlanSliceId.of(2L),
              PLAN_ID,
              PROVENANCE_CODE,
              SLICE_NO,
              SLICE_SIGNATURE_HASH,
              WINDOW_SPEC_JSON,
              EXPR_HASH,
              EXPR_SNAPSHOT_JSON,
              SliceStatus.ASSIGNED,
              0L);
      assertThat(converter.toEntity(assigned).getStatusCode()).isEqualTo("ASSIGNED");

      // FINISHED
      PlanSliceAggregate finished =
          PlanSliceAggregate.restore(
              PlanSliceId.of(3L),
              PLAN_ID,
              PROVENANCE_CODE,
              SLICE_NO,
              SLICE_SIGNATURE_HASH,
              WINDOW_SPEC_JSON,
              EXPR_HASH,
              EXPR_SNAPSHOT_JSON,
              SliceStatus.FINISHED,
              0L);
      assertThat(converter.toEntity(finished).getStatusCode()).isEqualTo("FINISHED");
    }
  }

  // ========== toAggregate() 转换测试 ==========

  @Nested
  @DisplayName("toAggregate() 转换测试")
  class ToAggregateConversionTests {

    @Test
    @DisplayName("应该正确转换包含所有字段的 PlanSliceDO")
    void shouldConvertFullDOToAggregate() {
      // Given: 创建完整的 PlanSliceDO
      PlanSliceDO sliceDO = new PlanSliceDO();
      sliceDO.setId(100L);
      sliceDO.setPlanId(PLAN_ID.value());
      sliceDO.setProvenanceCode(PROVENANCE_CODE.getCode());
      sliceDO.setSliceNo(SLICE_NO);
      sliceDO.setSliceSignatureHash(SLICE_SIGNATURE_HASH);
      sliceDO.setWindowSpec(JsonNodeMappings.jsonStringToNode(WINDOW_SPEC_JSON));
      sliceDO.setExprHash(EXPR_HASH);
      sliceDO.setExprSnapshot(JsonNodeMappings.jsonStringToNode(EXPR_SNAPSHOT_JSON));
      sliceDO.setStatusCode("ASSIGNED");
      sliceDO.setVersion(3L);

      // When: 转换为 Aggregate
      PlanSliceAggregate result = converter.toAggregate(sliceDO);

      // Then: 验证转换结果
      assertThat(result).isNotNull();
      assertThat(result.getId().value()).isEqualTo(100L);
      assertThat(result.getPlanId()).isEqualTo(PLAN_ID);
      assertThat(result.getProvenanceCode()).isEqualTo(PROVENANCE_CODE);
      assertThat(result.getSliceNo()).isEqualTo(SLICE_NO);
      assertThat(result.getSliceSignatureHash()).isEqualTo(SLICE_SIGNATURE_HASH);
      assertThat(result.getWindowSpecJson()).isEqualTo(WINDOW_SPEC_JSON);
      assertThat(result.getExprHash()).isEqualTo(EXPR_HASH);
      assertThat(result.getExprSnapshotJson()).isEqualTo(EXPR_SNAPSHOT_JSON);
      assertThat(result.getStatus()).isEqualTo(SliceStatus.ASSIGNED);
      assertThat(result.getVersion()).isEqualTo(3L);
    }

    @Test
    @DisplayName("应该正确转换 PENDING 状态的 PlanSliceDO")
    void shouldConvertPendingStatusDO() {
      // Given: 创建 PENDING 状态的 PlanSliceDO
      PlanSliceDO sliceDO = new PlanSliceDO();
      sliceDO.setId(200L);
      sliceDO.setPlanId(PLAN_ID.value());
      sliceDO.setProvenanceCode(PROVENANCE_CODE.getCode());
      sliceDO.setSliceNo(SLICE_NO);
      sliceDO.setSliceSignatureHash(SLICE_SIGNATURE_HASH);
      sliceDO.setWindowSpec(JsonNodeMappings.jsonStringToNode(WINDOW_SPEC_JSON));
      sliceDO.setExprHash(EXPR_HASH);
      sliceDO.setExprSnapshot(JsonNodeMappings.jsonStringToNode(EXPR_SNAPSHOT_JSON));
      sliceDO.setStatusCode("PENDING");
      sliceDO.setVersion(0L);

      // When: 转换为 Aggregate
      PlanSliceAggregate result = converter.toAggregate(sliceDO);

      // Then: 验证状态
      assertThat(result.getStatus()).isEqualTo(SliceStatus.PENDING);
    }

    @Test
    @DisplayName("应该正确转换 FINISHED 状态的 PlanSliceDO")
    void shouldConvertFinishedStatusDO() {
      // Given: 创建 FINISHED 状态的 PlanSliceDO
      PlanSliceDO sliceDO = new PlanSliceDO();
      sliceDO.setId(300L);
      sliceDO.setPlanId(PLAN_ID.value());
      sliceDO.setProvenanceCode(PROVENANCE_CODE.getCode());
      sliceDO.setSliceNo(SLICE_NO);
      sliceDO.setSliceSignatureHash(SLICE_SIGNATURE_HASH);
      sliceDO.setWindowSpec(JsonNodeMappings.jsonStringToNode(WINDOW_SPEC_JSON));
      sliceDO.setExprHash(EXPR_HASH);
      sliceDO.setExprSnapshot(JsonNodeMappings.jsonStringToNode(EXPR_SNAPSHOT_JSON));
      sliceDO.setStatusCode("FINISHED");
      sliceDO.setVersion(0L);

      // When: 转换为 Aggregate
      PlanSliceAggregate result = converter.toAggregate(sliceDO);

      // Then: 验证状态
      assertThat(result.getStatus()).isEqualTo(SliceStatus.FINISHED);
    }

    @Test
    @DisplayName("应该正确处理 sliceNo 为 null 的情况（默认为 0）")
    void shouldDefaultToZeroWhenSliceNoIsNull() {
      // Given: sliceNo 为 null 的 PlanSliceDO
      PlanSliceDO sliceDO = new PlanSliceDO();
      sliceDO.setId(400L);
      sliceDO.setPlanId(PLAN_ID.value());
      sliceDO.setProvenanceCode(PROVENANCE_CODE.getCode());
      sliceDO.setSliceNo(null); // null sliceNo
      sliceDO.setSliceSignatureHash(SLICE_SIGNATURE_HASH);
      sliceDO.setWindowSpec(JsonNodeMappings.jsonStringToNode(WINDOW_SPEC_JSON));
      sliceDO.setExprHash(EXPR_HASH);
      sliceDO.setExprSnapshot(JsonNodeMappings.jsonStringToNode(EXPR_SNAPSHOT_JSON));
      sliceDO.setStatusCode("PENDING");
      sliceDO.setVersion(0L);

      // When: 转换为 Aggregate
      PlanSliceAggregate result = converter.toAggregate(sliceDO);

      // Then: 应默认为 0
      assertThat(result.getSliceNo()).isEqualTo(0);
    }

    @Test
    @DisplayName("应该正确处理 statusCode 为 null 的情况（默认为 PENDING）")
    void shouldDefaultToPendingWhenStatusCodeIsNull() {
      // Given: statusCode 为 null 的 PlanSliceDO
      PlanSliceDO sliceDO = new PlanSliceDO();
      sliceDO.setId(500L);
      sliceDO.setPlanId(PLAN_ID.value());
      sliceDO.setProvenanceCode(PROVENANCE_CODE.getCode());
      sliceDO.setSliceNo(SLICE_NO);
      sliceDO.setSliceSignatureHash(SLICE_SIGNATURE_HASH);
      sliceDO.setWindowSpec(JsonNodeMappings.jsonStringToNode(WINDOW_SPEC_JSON));
      sliceDO.setExprHash(EXPR_HASH);
      sliceDO.setExprSnapshot(JsonNodeMappings.jsonStringToNode(EXPR_SNAPSHOT_JSON));
      sliceDO.setStatusCode(null); // null statusCode
      sliceDO.setVersion(0L);

      // When: 转换为 Aggregate
      PlanSliceAggregate result = converter.toAggregate(sliceDO);

      // Then: 应默认为 PENDING
      assertThat(result.getStatus()).isEqualTo(SliceStatus.PENDING);
    }

    @Test
    @DisplayName("应该正确处理 null PlanSliceDO")
    void shouldReturnNullForNullDO() {
      // When: 转换 null DO
      PlanSliceAggregate result = converter.toAggregate(null);

      // Then: 应返回 null
      assertThat(result).isNull();
    }
  }

  // ========== 静态辅助方法测试 ==========

  @Nested
  @DisplayName("静态辅助方法测试")
  class StaticHelperMethodTests {

    @Test
    @DisplayName("sliceStatusToCode() 应正确转换枚举")
    void shouldConvertSliceStatusToCode() {
      assertThat(PlanSliceConverter.sliceStatusToCode(SliceStatus.PENDING)).isEqualTo("PENDING");
      assertThat(PlanSliceConverter.sliceStatusToCode(SliceStatus.ASSIGNED)).isEqualTo("ASSIGNED");
      assertThat(PlanSliceConverter.sliceStatusToCode(SliceStatus.FINISHED)).isEqualTo("FINISHED");
      assertThat(PlanSliceConverter.sliceStatusToCode(null)).isNull();
    }

    @Test
    @DisplayName("sliceStatusFromCode() 应正确转换代码")
    void shouldConvertCodeToSliceStatus() {
      assertThat(PlanSliceConverter.sliceStatusFromCode("PENDING")).isEqualTo(SliceStatus.PENDING);
      assertThat(PlanSliceConverter.sliceStatusFromCode("ASSIGNED"))
          .isEqualTo(SliceStatus.ASSIGNED);
      assertThat(PlanSliceConverter.sliceStatusFromCode("FINISHED"))
          .isEqualTo(SliceStatus.FINISHED);
      assertThat(PlanSliceConverter.sliceStatusFromCode(null))
          .isEqualTo(SliceStatus.PENDING); // 默认值
    }
  }

  // ========== 双向转换一致性测试 ==========

  @Nested
  @DisplayName("双向转换一致性测试")
  class RoundTripConsistencyTests {

    @Test
    @DisplayName("ASSIGNED 状态的 Aggregate 应通过双向转换保持一致")
    void shouldMaintainConsistencyForAssignedStatusRoundTrip() {
      // Given: 原始 Aggregate
      PlanSliceAggregate original =
          PlanSliceAggregate.restore(
              PlanSliceId.of(100L),
              PLAN_ID,
              PROVENANCE_CODE,
              SLICE_NO,
              SLICE_SIGNATURE_HASH,
              WINDOW_SPEC_JSON,
              EXPR_HASH,
              EXPR_SNAPSHOT_JSON,
              SliceStatus.ASSIGNED,
              5L);

      // When: 双向转换
      PlanSliceDO entity = converter.toEntity(original);
      PlanSliceAggregate restored = converter.toAggregate(entity);

      // Then: 验证一致性
      assertThat(restored.getId()).isEqualTo(original.getId());
      assertThat(restored.getPlanId()).isEqualTo(original.getPlanId());
      assertThat(restored.getProvenanceCode()).isEqualTo(original.getProvenanceCode());
      assertThat(restored.getSliceNo()).isEqualTo(original.getSliceNo());
      assertThat(restored.getSliceSignatureHash()).isEqualTo(original.getSliceSignatureHash());
      assertThat(restored.getWindowSpecJson()).isEqualTo(original.getWindowSpecJson());
      assertThat(restored.getExprHash()).isEqualTo(original.getExprHash());
      assertThat(restored.getExprSnapshotJson()).isEqualTo(original.getExprSnapshotJson());
      assertThat(restored.getStatus()).isEqualTo(original.getStatus());
    }

    @Test
    @DisplayName("FINISHED 状态的 Aggregate 应通过双向转换保持一致")
    void shouldMaintainConsistencyForFinishedStatusRoundTrip() {
      // Given: 原始 Aggregate
      PlanSliceAggregate original =
          PlanSliceAggregate.restore(
              PlanSliceId.of(200L),
              PLAN_ID,
              PROVENANCE_CODE,
              SLICE_NO,
              SLICE_SIGNATURE_HASH,
              WINDOW_SPEC_JSON,
              EXPR_HASH,
              EXPR_SNAPSHOT_JSON,
              SliceStatus.FINISHED,
              10L);

      // When: 双向转换
      PlanSliceDO entity = converter.toEntity(original);
      PlanSliceAggregate restored = converter.toAggregate(entity);

      // Then: 验证一致性
      assertThat(restored.getStatus()).isEqualTo(SliceStatus.FINISHED);
      assertThat(restored.getSliceNo()).isEqualTo(SLICE_NO);
    }
  }
}
