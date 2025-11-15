package com.patra.ingest.infra.persistence.converter;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.common.enums.ProvenanceCode;
import com.patra.common.json.JsonNodeMappings;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.enums.PlanStatus;
import com.patra.ingest.domain.model.vo.plan.WindowSpec;
import com.patra.ingest.infra.persistence.entity.PlanDO;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * PlanConverter 单元测试。
 *
 * <p>测试策略：
 *
 * <ul>
 *   <li>测试 toEntity() 转换的正确性（Aggregate → DO）
 *   <li>测试 toAggregate() 转换的正确性（DO → Aggregate）
 *   <li>测试所有 PlanStatus 枚举值的转换
 *   <li>测试所有 WindowSpec 策略的转换
 *   <li>测试 JSON 字段的转换（exprProtoSnapshot, provenanceConfigSnapshot, sliceParams）
 *   <li>测试 denormalized 时间戳字段的处理（windowFromTs/windowToTs）
 *   <li>测试双向转换一致性
 *   <li>测试 null 安全性
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@DisplayName("PlanConverter 单元测试")
class PlanConverterTest {

  private final PlanConverter converter = new PlanConverterImpl();

  private static final Long SCHEDULE_INSTANCE_ID = 1001L;
  private static final String PLAN_KEY = "PUBMED:QUERY_SESSION:2025-01-01:2025-01-31";
  private static final ProvenanceCode PROVENANCE_CODE = ProvenanceCode.PUBMED;
  private static final String OPERATION_CODE = "HARVEST";
  private static final String EXPR_PROTO_HASH = "proto-hash-123";
  private static final String EXPR_PROTO_SNAPSHOT_JSON =
      "{\"type\":\"FETCH\",\"source\":\"pubmed\"}";
  private static final String PROVENANCE_CONFIG_SNAPSHOT_JSON =
      "{\"apiKey\":\"***\",\"endpoint\":\"https://api.pubmed.gov\"}";
  private static final String PROVENANCE_CONFIG_HASH = "config-hash-456";
  private static final String SLICE_STRATEGY_CODE = "TIME";
  private static final String SLICE_PARAMS_JSON = "{\"interval\":\"1h\",\"unit\":\"HOUR\"}";

  // ========== toEntity() 转换测试 ==========

  @Nested
  @DisplayName("toEntity() 转换测试")
  class ToEntityConversionTests {

    @Test
    @DisplayName("应该正确转换 TIME 策略的 PlanAggregate")
    void shouldConvertTimeStrategyAggregateToEntity() {
      // Given: 创建 TIME 策略的 PlanAggregate
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-31T23:59:59Z");
      WindowSpec windowSpec = WindowSpec.ofTime(from, to);

      PlanAggregate aggregate =
          PlanAggregate.restore(
              100L,
              SCHEDULE_INSTANCE_ID,
              PLAN_KEY,
              PROVENANCE_CODE,
              OPERATION_CODE,
              EXPR_PROTO_HASH,
              EXPR_PROTO_SNAPSHOT_JSON,
              PROVENANCE_CONFIG_SNAPSHOT_JSON,
              PROVENANCE_CONFIG_HASH,
              windowSpec,
              SLICE_STRATEGY_CODE,
              SLICE_PARAMS_JSON,
              PlanStatus.DRAFT,
              0L);

      // When: 转换为 DO
      PlanDO result = converter.toEntity(aggregate);

      // Then: 验证基本字段
      assertThat(result).isNotNull();
      assertThat(result.getScheduleInstanceId()).isEqualTo(SCHEDULE_INSTANCE_ID);
      assertThat(result.getPlanKey()).isEqualTo(PLAN_KEY);
      assertThat(result.getProvenanceCode()).isEqualTo(PROVENANCE_CODE.getCode());
      assertThat(result.getOperationCode()).isEqualTo(OPERATION_CODE);
      assertThat(result.getExprProtoHash()).isEqualTo(EXPR_PROTO_HASH);
      assertThat(result.getProvenanceConfigHash()).isEqualTo(PROVENANCE_CONFIG_HASH);
      assertThat(result.getSliceStrategyCode()).isEqualTo(SLICE_STRATEGY_CODE);
      assertThat(result.getStatusCode()).isEqualTo("DRAFT");

      // 验证 JSON 字段转换
      assertThat(result.getExprProtoSnapshot()).isNotNull();
      assertThat(JsonNodeMappings.jsonNodeToString(result.getExprProtoSnapshot()))
          .isEqualTo(EXPR_PROTO_SNAPSHOT_JSON);
      assertThat(result.getProvenanceConfigSnapshot()).isNotNull();
      assertThat(JsonNodeMappings.jsonNodeToString(result.getProvenanceConfigSnapshot()))
          .isEqualTo(PROVENANCE_CONFIG_SNAPSHOT_JSON);
      assertThat(result.getSliceParams()).isNotNull();
      assertThat(JsonNodeMappings.jsonNodeToString(result.getSliceParams()))
          .isEqualTo(SLICE_PARAMS_JSON);

      // 验证 WindowSpec JSON 转换
      assertThat(result.getWindowSpec()).isNotNull();

      // 验证 denormalized 时间戳字段（TIME 策略）
      assertThat(result.getWindowFromTs()).isEqualTo(from);
      assertThat(result.getWindowToTs()).isEqualTo(to);
    }

    @Test
    @DisplayName("应该正确转换 ID_RANGE 策略的 PlanAggregate（denormalized 字段应为 null）")
    void shouldConvertIdRangeStrategyAggregate() {
      // Given: 创建 ID_RANGE 策略的 PlanAggregate
      WindowSpec windowSpec = WindowSpec.ofIdRange(1000000L, 2000000L);

      PlanAggregate aggregate =
          PlanAggregate.restore(
              200L,
              SCHEDULE_INSTANCE_ID,
              PLAN_KEY,
              PROVENANCE_CODE,
              OPERATION_CODE,
              EXPR_PROTO_HASH,
              EXPR_PROTO_SNAPSHOT_JSON,
              PROVENANCE_CONFIG_SNAPSHOT_JSON,
              PROVENANCE_CONFIG_HASH,
              windowSpec,
              "ID_RANGE",
              SLICE_PARAMS_JSON,
              PlanStatus.READY,
              0L);

      // When: 转换为 DO
      PlanDO result = converter.toEntity(aggregate);

      // Then: 验证 denormalized 字段为 null（非 TIME 策略）
      assertThat(result.getWindowFromTs()).isNull();
      assertThat(result.getWindowToTs()).isNull();
      assertThat(result.getStatusCode()).isEqualTo("READY");
    }

    @Test
    @DisplayName("应该正确转换 SINGLE 策略的 PlanAggregate")
    void shouldConvertSingleStrategyAggregate() {
      // Given: 创建 SINGLE 策略的 PlanAggregate
      WindowSpec windowSpec = WindowSpec.ofSingle();

      PlanAggregate aggregate =
          PlanAggregate.restore(
              300L,
              SCHEDULE_INSTANCE_ID,
              PLAN_KEY,
              PROVENANCE_CODE,
              OPERATION_CODE,
              EXPR_PROTO_HASH,
              EXPR_PROTO_SNAPSHOT_JSON,
              PROVENANCE_CONFIG_SNAPSHOT_JSON,
              PROVENANCE_CONFIG_HASH,
              windowSpec,
              "SINGLE",
              SLICE_PARAMS_JSON,
              PlanStatus.SLICING,
              0L);

      // When: 转换为 DO
      PlanDO result = converter.toEntity(aggregate);

      // Then: 验证
      assertThat(result.getWindowFromTs()).isNull();
      assertThat(result.getWindowToTs()).isNull();
      assertThat(result.getStatusCode()).isEqualTo("SLICING");
    }

    @Test
    @DisplayName("应该正确转换所有 PlanStatus 枚举值")
    void shouldConvertAllPlanStatusValues() {
      WindowSpec windowSpec = WindowSpec.ofSingle();

      // DRAFT
      PlanAggregate draft =
          PlanAggregate.restore(
              1L,
              SCHEDULE_INSTANCE_ID,
              PLAN_KEY,
              PROVENANCE_CODE,
              OPERATION_CODE,
              EXPR_PROTO_HASH,
              EXPR_PROTO_SNAPSHOT_JSON,
              PROVENANCE_CONFIG_SNAPSHOT_JSON,
              PROVENANCE_CONFIG_HASH,
              windowSpec,
              SLICE_STRATEGY_CODE,
              SLICE_PARAMS_JSON,
              PlanStatus.DRAFT,
              0L);
      assertThat(converter.toEntity(draft).getStatusCode()).isEqualTo("DRAFT");

      // SLICING
      PlanAggregate slicing =
          PlanAggregate.restore(
              2L,
              SCHEDULE_INSTANCE_ID,
              PLAN_KEY,
              PROVENANCE_CODE,
              OPERATION_CODE,
              EXPR_PROTO_HASH,
              EXPR_PROTO_SNAPSHOT_JSON,
              PROVENANCE_CONFIG_SNAPSHOT_JSON,
              PROVENANCE_CONFIG_HASH,
              windowSpec,
              SLICE_STRATEGY_CODE,
              SLICE_PARAMS_JSON,
              PlanStatus.SLICING,
              0L);
      assertThat(converter.toEntity(slicing).getStatusCode()).isEqualTo("SLICING");

      // READY
      PlanAggregate ready =
          PlanAggregate.restore(
              3L,
              SCHEDULE_INSTANCE_ID,
              PLAN_KEY,
              PROVENANCE_CODE,
              OPERATION_CODE,
              EXPR_PROTO_HASH,
              EXPR_PROTO_SNAPSHOT_JSON,
              PROVENANCE_CONFIG_SNAPSHOT_JSON,
              PROVENANCE_CONFIG_HASH,
              windowSpec,
              SLICE_STRATEGY_CODE,
              SLICE_PARAMS_JSON,
              PlanStatus.READY,
              0L);
      assertThat(converter.toEntity(ready).getStatusCode()).isEqualTo("READY");

      // ARCHIVED
      PlanAggregate archived =
          PlanAggregate.restore(
              4L,
              SCHEDULE_INSTANCE_ID,
              PLAN_KEY,
              PROVENANCE_CODE,
              OPERATION_CODE,
              EXPR_PROTO_HASH,
              EXPR_PROTO_SNAPSHOT_JSON,
              PROVENANCE_CONFIG_SNAPSHOT_JSON,
              PROVENANCE_CONFIG_HASH,
              windowSpec,
              SLICE_STRATEGY_CODE,
              SLICE_PARAMS_JSON,
              PlanStatus.ARCHIVED,
              0L);
      assertThat(converter.toEntity(archived).getStatusCode()).isEqualTo("ARCHIVED");
    }
  }

  // ========== toAggregate() 转换测试 ==========

  @Nested
  @DisplayName("toAggregate() 转换测试")
  class ToAggregateConversionTests {

    @Test
    @DisplayName("应该正确转换包含所有字段的 PlanDO（TIME 策略）")
    void shouldConvertFullDOToAggregate() {
      // Given: 创建完整的 PlanDO
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-31T23:59:59Z");

      PlanDO planDO = new PlanDO();
      planDO.setId(100L);
      planDO.setScheduleInstanceId(SCHEDULE_INSTANCE_ID);
      planDO.setPlanKey(PLAN_KEY);
      planDO.setProvenanceCode(PROVENANCE_CODE.getCode());
      planDO.setOperationCode(OPERATION_CODE);
      planDO.setExprProtoHash(EXPR_PROTO_HASH);
      planDO.setExprProtoSnapshot(JsonNodeMappings.jsonStringToNode(EXPR_PROTO_SNAPSHOT_JSON));
      planDO.setProvenanceConfigSnapshot(
          JsonNodeMappings.jsonStringToNode(PROVENANCE_CONFIG_SNAPSHOT_JSON));
      planDO.setProvenanceConfigHash(PROVENANCE_CONFIG_HASH);
      planDO.setSliceStrategyCode(SLICE_STRATEGY_CODE);
      planDO.setSliceParams(JsonNodeMappings.jsonStringToNode(SLICE_PARAMS_JSON));
      planDO.setStatusCode("DRAFT");

      // 设置 WindowSpec JSON (TIME 策略)
      WindowSpec timeSpec = WindowSpec.ofTime(from, to);
      planDO.setWindowSpec(PlanConverter.windowSpecToJson(timeSpec));
      planDO.setWindowFromTs(from);
      planDO.setWindowToTs(to);
      planDO.setVersion(5L);

      // When: 转换为 Aggregate
      PlanAggregate result = converter.toAggregate(planDO);

      // Then: 验证转换结果
      assertThat(result).isNotNull();
      assertThat(result.getId()).isEqualTo(100L);
      assertThat(result.getScheduleInstanceId()).isEqualTo(SCHEDULE_INSTANCE_ID);
      assertThat(result.getPlanKey()).isEqualTo(PLAN_KEY);
      assertThat(result.getProvenanceCode()).isEqualTo(PROVENANCE_CODE);
      assertThat(result.getOperationCode()).isEqualTo(OPERATION_CODE);
      assertThat(result.getExprProtoHash()).isEqualTo(EXPR_PROTO_HASH);
      assertThat(result.getExprProtoSnapshotJson()).isEqualTo(EXPR_PROTO_SNAPSHOT_JSON);
      assertThat(result.getProvenanceConfigSnapshotJson())
          .isEqualTo(PROVENANCE_CONFIG_SNAPSHOT_JSON);
      assertThat(result.getProvenanceConfigHash()).isEqualTo(PROVENANCE_CONFIG_HASH);
      assertThat(result.getSliceStrategyCode()).isEqualTo(SLICE_STRATEGY_CODE);
      assertThat(result.getSliceParamsJson()).isEqualTo(SLICE_PARAMS_JSON);
      assertThat(result.getStatus()).isEqualTo(PlanStatus.DRAFT);
      assertThat(result.getVersion()).isEqualTo(5L);

      // 验证 WindowSpec 转换
      assertThat(result.getWindowSpec()).isInstanceOf(WindowSpec.Time.class);
      WindowSpec.Time timeWindow = (WindowSpec.Time) result.getWindowSpec();
      assertThat(timeWindow.from()).isEqualTo(from);
      assertThat(timeWindow.to()).isEqualTo(to);
    }

    @Test
    @DisplayName("应该正确转换 ID_RANGE 策略的 PlanDO")
    void shouldConvertIdRangeStrategyDO() {
      // Given: 创建 ID_RANGE 策略的 PlanDO
      WindowSpec idRangeSpec = WindowSpec.ofIdRange(1000000L, 2000000L);

      PlanDO planDO = new PlanDO();
      planDO.setId(200L);
      planDO.setScheduleInstanceId(SCHEDULE_INSTANCE_ID);
      planDO.setPlanKey(PLAN_KEY);
      planDO.setProvenanceCode(PROVENANCE_CODE.getCode());
      planDO.setOperationCode(OPERATION_CODE);
      planDO.setExprProtoHash(EXPR_PROTO_HASH);
      planDO.setExprProtoSnapshot(JsonNodeMappings.jsonStringToNode(EXPR_PROTO_SNAPSHOT_JSON));
      planDO.setProvenanceConfigSnapshot(
          JsonNodeMappings.jsonStringToNode(PROVENANCE_CONFIG_SNAPSHOT_JSON));
      planDO.setProvenanceConfigHash(PROVENANCE_CONFIG_HASH);
      planDO.setSliceStrategyCode("ID_RANGE");
      planDO.setSliceParams(JsonNodeMappings.jsonStringToNode(SLICE_PARAMS_JSON));
      planDO.setWindowSpec(PlanConverter.windowSpecToJson(idRangeSpec));
      planDO.setStatusCode("READY");
      planDO.setVersion(0L);

      // When: 转换为 Aggregate
      PlanAggregate result = converter.toAggregate(planDO);

      // Then: 验证 WindowSpec
      assertThat(result.getWindowSpec()).isInstanceOf(WindowSpec.IdRange.class);
      WindowSpec.IdRange idRangeWindow = (WindowSpec.IdRange) result.getWindowSpec();
      assertThat(idRangeWindow.from()).isEqualTo(1000000L);
      assertThat(idRangeWindow.to()).isEqualTo(2000000L);
      assertThat(result.getStatus()).isEqualTo(PlanStatus.READY);
    }

    @Test
    @DisplayName("应该正确转换 CURSOR_LANDMARK 策略的 PlanDO")
    void shouldConvertCursorLandmarkStrategyDO() {
      // Given: 创建 CURSOR_LANDMARK 策略的 PlanDO
      WindowSpec cursorSpec = WindowSpec.ofCursor("cursor-token-start", "cursor-token-end");

      PlanDO planDO = new PlanDO();
      planDO.setId(300L);
      planDO.setScheduleInstanceId(SCHEDULE_INSTANCE_ID);
      planDO.setPlanKey(PLAN_KEY);
      planDO.setProvenanceCode(PROVENANCE_CODE.getCode());
      planDO.setOperationCode(OPERATION_CODE);
      planDO.setExprProtoHash(EXPR_PROTO_HASH);
      planDO.setExprProtoSnapshot(JsonNodeMappings.jsonStringToNode(EXPR_PROTO_SNAPSHOT_JSON));
      planDO.setProvenanceConfigSnapshot(
          JsonNodeMappings.jsonStringToNode(PROVENANCE_CONFIG_SNAPSHOT_JSON));
      planDO.setProvenanceConfigHash(PROVENANCE_CONFIG_HASH);
      planDO.setSliceStrategyCode("CURSOR_LANDMARK");
      planDO.setSliceParams(JsonNodeMappings.jsonStringToNode(SLICE_PARAMS_JSON));
      planDO.setWindowSpec(PlanConverter.windowSpecToJson(cursorSpec));
      planDO.setStatusCode("SLICING");
      planDO.setVersion(0L);

      // When: 转换为 Aggregate
      PlanAggregate result = converter.toAggregate(planDO);

      // Then: 验证 WindowSpec
      assertThat(result.getWindowSpec()).isInstanceOf(WindowSpec.CursorLandmark.class);
      WindowSpec.CursorLandmark cursorWindow = (WindowSpec.CursorLandmark) result.getWindowSpec();
      assertThat(cursorWindow.from()).isEqualTo("cursor-token-start");
      assertThat(cursorWindow.to()).isEqualTo("cursor-token-end");
      assertThat(result.getStatus()).isEqualTo(PlanStatus.SLICING);
    }

    @Test
    @DisplayName("应该正确转换 VOLUME_BUDGET 策略的 PlanDO")
    void shouldConvertVolumeBudgetStrategyDO() {
      // Given: 创建 VOLUME_BUDGET 策略的 PlanDO
      WindowSpec volumeSpec = WindowSpec.ofVolume(100000, "RECORDS");

      PlanDO planDO = new PlanDO();
      planDO.setId(400L);
      planDO.setScheduleInstanceId(SCHEDULE_INSTANCE_ID);
      planDO.setPlanKey(PLAN_KEY);
      planDO.setProvenanceCode(PROVENANCE_CODE.getCode());
      planDO.setOperationCode(OPERATION_CODE);
      planDO.setExprProtoHash(EXPR_PROTO_HASH);
      planDO.setExprProtoSnapshot(JsonNodeMappings.jsonStringToNode(EXPR_PROTO_SNAPSHOT_JSON));
      planDO.setProvenanceConfigSnapshot(
          JsonNodeMappings.jsonStringToNode(PROVENANCE_CONFIG_SNAPSHOT_JSON));
      planDO.setProvenanceConfigHash(PROVENANCE_CONFIG_HASH);
      planDO.setSliceStrategyCode("VOLUME_BUDGET");
      planDO.setSliceParams(JsonNodeMappings.jsonStringToNode(SLICE_PARAMS_JSON));
      planDO.setWindowSpec(PlanConverter.windowSpecToJson(volumeSpec));
      planDO.setStatusCode("ARCHIVED");
      planDO.setVersion(0L);

      // When: 转换为 Aggregate
      PlanAggregate result = converter.toAggregate(planDO);

      // Then: 验证 WindowSpec
      assertThat(result.getWindowSpec()).isInstanceOf(WindowSpec.VolumeBudget.class);
      WindowSpec.VolumeBudget volumeWindow = (WindowSpec.VolumeBudget) result.getWindowSpec();
      assertThat(volumeWindow.limit()).isEqualTo(100000);
      assertThat(volumeWindow.unit()).isEqualTo("RECORDS");
      assertThat(result.getStatus()).isEqualTo(PlanStatus.ARCHIVED);
    }

    @Test
    @DisplayName("应该正确转换 SINGLE 策略的 PlanDO")
    void shouldConvertSingleStrategyDO() {
      // Given: 创建 SINGLE 策略的 PlanDO
      WindowSpec singleSpec = WindowSpec.ofSingle();

      PlanDO planDO = new PlanDO();
      planDO.setId(500L);
      planDO.setScheduleInstanceId(SCHEDULE_INSTANCE_ID);
      planDO.setPlanKey(PLAN_KEY);
      planDO.setProvenanceCode(PROVENANCE_CODE.getCode());
      planDO.setOperationCode(OPERATION_CODE);
      planDO.setExprProtoHash(EXPR_PROTO_HASH);
      planDO.setExprProtoSnapshot(JsonNodeMappings.jsonStringToNode(EXPR_PROTO_SNAPSHOT_JSON));
      planDO.setProvenanceConfigSnapshot(
          JsonNodeMappings.jsonStringToNode(PROVENANCE_CONFIG_SNAPSHOT_JSON));
      planDO.setProvenanceConfigHash(PROVENANCE_CONFIG_HASH);
      planDO.setSliceStrategyCode("SINGLE");
      planDO.setSliceParams(JsonNodeMappings.jsonStringToNode(SLICE_PARAMS_JSON));
      planDO.setWindowSpec(PlanConverter.windowSpecToJson(singleSpec));
      planDO.setStatusCode("DRAFT");
      planDO.setVersion(0L);

      // When: 转换为 Aggregate
      PlanAggregate result = converter.toAggregate(planDO);

      // Then: 验证 WindowSpec
      assertThat(result.getWindowSpec()).isInstanceOf(WindowSpec.Single.class);
      assertThat(result.getStatus()).isEqualTo(PlanStatus.DRAFT);
    }

    @Test
    @DisplayName("应该正确处理 null PlanDO")
    void shouldReturnNullForNullDO() {
      // When: 转换 null DO
      PlanAggregate result = converter.toAggregate(null);

      // Then: 应返回 null
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("应该正确处理 statusCode 为 null 的情况（默认为 DRAFT）")
    void shouldDefaultToDraftWhenStatusCodeIsNull() {
      // Given: statusCode 为 null 的 PlanDO
      WindowSpec windowSpec = WindowSpec.ofSingle();

      PlanDO planDO = new PlanDO();
      planDO.setId(600L);
      planDO.setScheduleInstanceId(SCHEDULE_INSTANCE_ID);
      planDO.setPlanKey(PLAN_KEY);
      planDO.setProvenanceCode(PROVENANCE_CODE.getCode());
      planDO.setOperationCode(OPERATION_CODE);
      planDO.setExprProtoHash(EXPR_PROTO_HASH);
      planDO.setExprProtoSnapshot(JsonNodeMappings.jsonStringToNode(EXPR_PROTO_SNAPSHOT_JSON));
      planDO.setProvenanceConfigSnapshot(
          JsonNodeMappings.jsonStringToNode(PROVENANCE_CONFIG_SNAPSHOT_JSON));
      planDO.setProvenanceConfigHash(PROVENANCE_CONFIG_HASH);
      planDO.setSliceStrategyCode("SINGLE");
      planDO.setSliceParams(JsonNodeMappings.jsonStringToNode(SLICE_PARAMS_JSON));
      planDO.setWindowSpec(PlanConverter.windowSpecToJson(windowSpec));
      planDO.setStatusCode(null); // null statusCode
      planDO.setVersion(0L);

      // When: 转换为 Aggregate
      PlanAggregate result = converter.toAggregate(planDO);

      // Then: 应默认为 DRAFT
      assertThat(result.getStatus()).isEqualTo(PlanStatus.DRAFT);
    }
  }

  // ========== 静态辅助方法测试 ==========

  @Nested
  @DisplayName("静态辅助方法测试")
  class StaticHelperMethodTests {

    @Test
    @DisplayName("planStatusToCode() 应正确转换枚举")
    void shouldConvertPlanStatusToCode() {
      assertThat(PlanConverter.planStatusToCode(PlanStatus.DRAFT)).isEqualTo("DRAFT");
      assertThat(PlanConverter.planStatusToCode(PlanStatus.SLICING)).isEqualTo("SLICING");
      assertThat(PlanConverter.planStatusToCode(PlanStatus.READY)).isEqualTo("READY");
      assertThat(PlanConverter.planStatusToCode(PlanStatus.ARCHIVED)).isEqualTo("ARCHIVED");
      assertThat(PlanConverter.planStatusToCode(null)).isNull();
    }

    @Test
    @DisplayName("planStatusFromCode() 应正确转换代码")
    void shouldConvertCodeToPlanStatus() {
      assertThat(PlanConverter.planStatusFromCode("DRAFT")).isEqualTo(PlanStatus.DRAFT);
      assertThat(PlanConverter.planStatusFromCode("SLICING")).isEqualTo(PlanStatus.SLICING);
      assertThat(PlanConverter.planStatusFromCode("READY")).isEqualTo(PlanStatus.READY);
      assertThat(PlanConverter.planStatusFromCode("ARCHIVED")).isEqualTo(PlanStatus.ARCHIVED);
      assertThat(PlanConverter.planStatusFromCode(null)).isEqualTo(PlanStatus.DRAFT); // 默认值
    }

    @Test
    @DisplayName("windowSpecToJson() 应正确转换 TIME WindowSpec")
    void shouldConvertTimeWindowSpecToJson() {
      // Given: TIME WindowSpec
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-31T23:59:59Z");
      WindowSpec windowSpec = WindowSpec.ofTime(from, to);

      // When: 转换为 JSON
      JsonNode result = PlanConverter.windowSpecToJson(windowSpec);

      // Then: 验证 JSON 结构
      assertThat(result).isNotNull();
      assertThat(result.has("strategy")).isTrue();
      assertThat(result.get("strategy").asText()).isEqualTo("TIME");
      assertThat(result.has("window")).isTrue();
    }

    @Test
    @DisplayName("windowSpecToJson() 应正确转换 SINGLE WindowSpec")
    void shouldConvertSingleWindowSpecToJson() {
      // Given: SINGLE WindowSpec
      WindowSpec windowSpec = WindowSpec.ofSingle();

      // When: 转换为 JSON
      JsonNode result = PlanConverter.windowSpecToJson(windowSpec);

      // Then: 验证 JSON 结构
      assertThat(result).isNotNull();
      assertThat(result.has("strategy")).isTrue();
      assertThat(result.get("strategy").asText()).isEqualTo("SINGLE");
    }

    @Test
    @DisplayName("windowSpecToJson() 在 WindowSpec 为 null 时应返回 null")
    void shouldReturnNullForNullWindowSpec() {
      assertThat(PlanConverter.windowSpecToJson(null)).isNull();
    }

    @Test
    @DisplayName("jsonToWindowSpec() 应正确解析 TIME JSON")
    void shouldParseTimeWindowSpecFromJson() {
      // Given: TIME JSON
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-31T23:59:59Z");
      WindowSpec originalSpec = WindowSpec.ofTime(from, to);
      JsonNode json = PlanConverter.windowSpecToJson(originalSpec);

      // When: 解析 JSON
      WindowSpec result = PlanConverter.jsonToWindowSpec(json);

      // Then: 验证结果
      assertThat(result).isInstanceOf(WindowSpec.Time.class);
      WindowSpec.Time timeWindow = (WindowSpec.Time) result;
      assertThat(timeWindow.from()).isEqualTo(from);
      assertThat(timeWindow.to()).isEqualTo(to);
    }

    @Test
    @DisplayName("jsonToWindowSpec() 应正确处理 null JSON")
    void shouldReturnNullForNullJson() {
      assertThat(PlanConverter.jsonToWindowSpec(null)).isNull();
    }
  }

  // ========== 双向转换一致性测试 ==========

  @Nested
  @DisplayName("双向转换一致性测试")
  class RoundTripConsistencyTests {

    @Test
    @DisplayName("TIME 策略的 Aggregate 应通过双向转换保持一致")
    void shouldMaintainConsistencyForTimeStrategyRoundTrip() {
      // Given: 原始 Aggregate
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      Instant to = Instant.parse("2025-01-31T23:59:59Z");
      WindowSpec windowSpec = WindowSpec.ofTime(from, to);

      PlanAggregate original =
          PlanAggregate.restore(
              100L,
              SCHEDULE_INSTANCE_ID,
              PLAN_KEY,
              PROVENANCE_CODE,
              OPERATION_CODE,
              EXPR_PROTO_HASH,
              EXPR_PROTO_SNAPSHOT_JSON,
              PROVENANCE_CONFIG_SNAPSHOT_JSON,
              PROVENANCE_CONFIG_HASH,
              windowSpec,
              SLICE_STRATEGY_CODE,
              SLICE_PARAMS_JSON,
              PlanStatus.READY,
              3L);

      // When: 双向转换
      PlanDO entity = converter.toEntity(original);
      PlanAggregate restored = converter.toAggregate(entity);

      // Then: 验证一致性
      assertThat(restored.getId()).isEqualTo(original.getId());
      assertThat(restored.getScheduleInstanceId()).isEqualTo(original.getScheduleInstanceId());
      assertThat(restored.getPlanKey()).isEqualTo(original.getPlanKey());
      assertThat(restored.getProvenanceCode()).isEqualTo(original.getProvenanceCode());
      assertThat(restored.getOperationCode()).isEqualTo(original.getOperationCode());
      assertThat(restored.getExprProtoHash()).isEqualTo(original.getExprProtoHash());
      assertThat(restored.getProvenanceConfigHash()).isEqualTo(original.getProvenanceConfigHash());
      assertThat(restored.getSliceStrategyCode()).isEqualTo(original.getSliceStrategyCode());
      assertThat(restored.getStatus()).isEqualTo(original.getStatus());

      // 验证 WindowSpec
      assertThat(restored.getWindowSpec()).isInstanceOf(WindowSpec.Time.class);
      WindowSpec.Time restoredWindow = (WindowSpec.Time) restored.getWindowSpec();
      WindowSpec.Time originalWindow = (WindowSpec.Time) original.getWindowSpec();
      assertThat(restoredWindow.from()).isEqualTo(originalWindow.from());
      assertThat(restoredWindow.to()).isEqualTo(originalWindow.to());
    }

    @Test
    @DisplayName("SINGLE 策略的 Aggregate 应通过双向转换保持一致")
    void shouldMaintainConsistencyForSingleStrategyRoundTrip() {
      // Given: 原始 Aggregate
      WindowSpec windowSpec = WindowSpec.ofSingle();

      PlanAggregate original =
          PlanAggregate.restore(
              200L,
              SCHEDULE_INSTANCE_ID,
              PLAN_KEY,
              PROVENANCE_CODE,
              OPERATION_CODE,
              EXPR_PROTO_HASH,
              EXPR_PROTO_SNAPSHOT_JSON,
              PROVENANCE_CONFIG_SNAPSHOT_JSON,
              PROVENANCE_CONFIG_HASH,
              windowSpec,
              "SINGLE",
              SLICE_PARAMS_JSON,
              PlanStatus.ARCHIVED,
              5L);

      // When: 双向转换
      PlanDO entity = converter.toEntity(original);
      PlanAggregate restored = converter.toAggregate(entity);

      // Then: 验证一致性
      assertThat(restored.getStatus()).isEqualTo(PlanStatus.ARCHIVED);
      assertThat(restored.getWindowSpec()).isInstanceOf(WindowSpec.Single.class);
    }
  }
}
