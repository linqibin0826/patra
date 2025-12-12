package com.patra.ingest.infra.persistence.converter;

import static org.assertj.core.api.Assertions.*;

import com.patra.common.enums.ProvenanceCode;
import com.patra.common.json.JsonNodeMappings;
import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;
import com.patra.ingest.domain.model.enums.Scheduler;
import com.patra.ingest.domain.model.enums.TriggerType;
import com.patra.ingest.domain.model.vo.schedule.ScheduleInstanceId;
import com.patra.ingest.infra.persistence.entity.ScheduleInstanceDO;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// ScheduleInstanceConverter 单元测试。
///
/// 测试策略：
///
/// - 测试 toDO() 转换的正确性（Aggregate → DO）
///   - 测试 toDomain() 转换的正确性（DO → Aggregate）
///   - 测试所有 Scheduler 枚举值的转换
///   - 测试所有 TriggerType 枚举值的转换
///   - 测试 triggerParams Map → JsonNode 的转换
///   - 测试双向转换一致性
///   - 测试 null 安全性
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("ScheduleInstanceConverter 单元测试")
class ScheduleInstanceConverterTest {

  private final ScheduleInstanceConverter converter = new ScheduleInstanceConverterImpl();

  private static final String SCHEDULER_JOB_ID = "job-12345";
  private static final String SCHEDULER_LOG_ID = "log-67890";
  private static final Instant TRIGGERED_AT = Instant.parse("2025-01-15T10:00:00Z");
  private static final ProvenanceCode PROVENANCE_CODE = ProvenanceCode.PUBMED;
  private static final Map<String, Object> TRIGGER_PARAMS =
      Map.of("window", "2025-01", "mode", "incremental", "retryCount", 3);

  // ========== toDO() 转换测试 ==========

  @Nested
  @DisplayName("toDO() 转换测试")
  class ToDOConversionTests {

    @Test
    @DisplayName("应该正确转换 XXL + SCHEDULE 触发类型的 ScheduleInstanceAggregate")
    void shouldConvertXxlScheduleAggregateToEntity() {
      // Given: 创建 XXL + SCHEDULE 触发类型的 ScheduleInstanceAggregate
      ScheduleInstanceAggregate aggregate =
          ScheduleInstanceAggregate.restore(
              ScheduleInstanceId.of(100L),
              Scheduler.XXL,
              SCHEDULER_JOB_ID,
              SCHEDULER_LOG_ID,
              TriggerType.SCHEDULE,
              TRIGGERED_AT,
              TRIGGER_PARAMS,
              PROVENANCE_CODE,
              0L);

      // When: 转换为 DO
      ScheduleInstanceDO result = converter.toDO(aggregate);

      // Then: 验证基本字段
      assertThat(result).isNotNull();
      assertThat(result.getSchedulerCode()).isEqualTo("XXL");
      assertThat(result.getSchedulerJobId()).isEqualTo(SCHEDULER_JOB_ID);
      assertThat(result.getSchedulerLogId()).isEqualTo(SCHEDULER_LOG_ID);
      assertThat(result.getTriggerTypeCode()).isEqualTo("SCHEDULE");
      assertThat(result.getTriggeredAt()).isEqualTo(TRIGGERED_AT);
      assertThat(result.getProvenanceCode()).isEqualTo(PROVENANCE_CODE.getCode());

      // 验证 triggerParams JSON 转换
      assertThat(result.getTriggerParams()).isNotNull();
      Map<String, Object> restoredParams =
          JsonNodeMappings.jsonNodeToMap(result.getTriggerParams());
      assertThat(restoredParams).containsEntry("window", "2025-01");
      assertThat(restoredParams).containsEntry("mode", "incremental");
      assertThat(restoredParams).containsEntry("retryCount", 3);
    }

    @Test
    @DisplayName("应该正确转换 SPRING + MANUAL 触发类型的 ScheduleInstanceAggregate")
    void shouldConvertSpringManualAggregate() {
      // Given: 创建 SPRING + MANUAL 触发类型的 ScheduleInstanceAggregate
      ScheduleInstanceAggregate aggregate =
          ScheduleInstanceAggregate.restore(
              ScheduleInstanceId.of(200L),
              Scheduler.SPRING,
              SCHEDULER_JOB_ID,
              SCHEDULER_LOG_ID,
              TriggerType.MANUAL,
              TRIGGERED_AT,
              TRIGGER_PARAMS,
              PROVENANCE_CODE,
              0L);

      // When: 转换为 DO
      ScheduleInstanceDO result = converter.toDO(aggregate);

      // Then: 验证枚举字段
      assertThat(result.getSchedulerCode()).isEqualTo("SPRING");
      assertThat(result.getTriggerTypeCode()).isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("应该正确转换 QUARTZ + API 触发类型的 ScheduleInstanceAggregate")
    void shouldConvertQuartzApiAggregate() {
      // Given: 创建 QUARTZ + API 触发类型的 ScheduleInstanceAggregate
      ScheduleInstanceAggregate aggregate =
          ScheduleInstanceAggregate.restore(
              ScheduleInstanceId.of(300L),
              Scheduler.QUARTZ,
              SCHEDULER_JOB_ID,
              SCHEDULER_LOG_ID,
              TriggerType.API,
              TRIGGERED_AT,
              TRIGGER_PARAMS,
              PROVENANCE_CODE,
              0L);

      // When: 转换为 DO
      ScheduleInstanceDO result = converter.toDO(aggregate);

      // Then: 验证枚举字段
      assertThat(result.getSchedulerCode()).isEqualTo("QUARTZ");
      assertThat(result.getTriggerTypeCode()).isEqualTo("API");
    }

    @Test
    @DisplayName("应该正确转换所有 Scheduler 枚举值")
    void shouldConvertAllSchedulerValues() {
      // XXL
      ScheduleInstanceAggregate xxl =
          ScheduleInstanceAggregate.restore(
              ScheduleInstanceId.of(1L),
              Scheduler.XXL,
              SCHEDULER_JOB_ID,
              SCHEDULER_LOG_ID,
              TriggerType.SCHEDULE,
              TRIGGERED_AT,
              TRIGGER_PARAMS,
              PROVENANCE_CODE,
              0L);
      assertThat(converter.toDO(xxl).getSchedulerCode()).isEqualTo("XXL");

      // SPRING
      ScheduleInstanceAggregate spring =
          ScheduleInstanceAggregate.restore(
              ScheduleInstanceId.of(2L),
              Scheduler.SPRING,
              SCHEDULER_JOB_ID,
              SCHEDULER_LOG_ID,
              TriggerType.SCHEDULE,
              TRIGGERED_AT,
              TRIGGER_PARAMS,
              PROVENANCE_CODE,
              0L);
      assertThat(converter.toDO(spring).getSchedulerCode()).isEqualTo("SPRING");

      // QUARTZ
      ScheduleInstanceAggregate quartz =
          ScheduleInstanceAggregate.restore(
              ScheduleInstanceId.of(3L),
              Scheduler.QUARTZ,
              SCHEDULER_JOB_ID,
              SCHEDULER_LOG_ID,
              TriggerType.SCHEDULE,
              TRIGGERED_AT,
              TRIGGER_PARAMS,
              PROVENANCE_CODE,
              0L);
      assertThat(converter.toDO(quartz).getSchedulerCode()).isEqualTo("QUARTZ");
    }

    @Test
    @DisplayName("应该正确转换所有 TriggerType 枚举值")
    void shouldConvertAllTriggerTypeValues() {
      // SCHEDULE
      ScheduleInstanceAggregate schedule =
          ScheduleInstanceAggregate.restore(
              ScheduleInstanceId.of(1L),
              Scheduler.XXL,
              SCHEDULER_JOB_ID,
              SCHEDULER_LOG_ID,
              TriggerType.SCHEDULE,
              TRIGGERED_AT,
              TRIGGER_PARAMS,
              PROVENANCE_CODE,
              0L);
      assertThat(converter.toDO(schedule).getTriggerTypeCode()).isEqualTo("SCHEDULE");

      // MANUAL
      ScheduleInstanceAggregate manual =
          ScheduleInstanceAggregate.restore(
              ScheduleInstanceId.of(2L),
              Scheduler.XXL,
              SCHEDULER_JOB_ID,
              SCHEDULER_LOG_ID,
              TriggerType.MANUAL,
              TRIGGERED_AT,
              TRIGGER_PARAMS,
              PROVENANCE_CODE,
              0L);
      assertThat(converter.toDO(manual).getTriggerTypeCode()).isEqualTo("MANUAL");

      // API
      ScheduleInstanceAggregate api =
          ScheduleInstanceAggregate.restore(
              ScheduleInstanceId.of(3L),
              Scheduler.XXL,
              SCHEDULER_JOB_ID,
              SCHEDULER_LOG_ID,
              TriggerType.API,
              TRIGGERED_AT,
              TRIGGER_PARAMS,
              PROVENANCE_CODE,
              0L);
      assertThat(converter.toDO(api).getTriggerTypeCode()).isEqualTo("API");
    }

    @Test
    @DisplayName("应该正确处理空的 triggerParams")
    void shouldHandleEmptyTriggerParams() {
      // Given: triggerParams 为空 Map 的 ScheduleInstanceAggregate
      ScheduleInstanceAggregate aggregate =
          ScheduleInstanceAggregate.restore(
              ScheduleInstanceId.of(400L),
              Scheduler.XXL,
              SCHEDULER_JOB_ID,
              SCHEDULER_LOG_ID,
              TriggerType.SCHEDULE,
              TRIGGERED_AT,
              Map.of(), // 空 Map
              PROVENANCE_CODE,
              0L);

      // When: 转换为 DO
      ScheduleInstanceDO result = converter.toDO(aggregate);

      // Then: triggerParams 可能为 null 或空 JSON 对象（取决于 mapToJsonNode 实现）
      if (result.getTriggerParams() != null) {
        Map<String, Object> restoredParams =
            JsonNodeMappings.jsonNodeToMap(result.getTriggerParams());
        assertThat(restoredParams).isEmpty();
      }
    }
  }

  // ========== toDomain() 转换测试 ==========

  @Nested
  @DisplayName("toDomain() 转换测试")
  class ToDomainConversionTests {

    @Test
    @DisplayName("应该正确转换包含所有字段的 ScheduleInstanceDO")
    void shouldConvertFullDOToDomain() {
      // Given: 创建完整的 ScheduleInstanceDO
      ScheduleInstanceDO instanceDO = new ScheduleInstanceDO();
      instanceDO.setId(100L);
      instanceDO.setSchedulerCode("XXL");
      instanceDO.setSchedulerJobId(SCHEDULER_JOB_ID);
      instanceDO.setSchedulerLogId(SCHEDULER_LOG_ID);
      instanceDO.setTriggerTypeCode("SCHEDULE");
      instanceDO.setTriggeredAt(TRIGGERED_AT);
      instanceDO.setTriggerParams(JsonNodeMappings.mapToJsonNode(TRIGGER_PARAMS));
      instanceDO.setProvenanceCode(PROVENANCE_CODE.getCode());
      instanceDO.setVersion(5L);

      // When: 转换为 Aggregate
      ScheduleInstanceAggregate result = converter.toDomain(instanceDO);

      // Then: 验证转换结果
      assertThat(result).isNotNull();
      assertThat(result.getId().value()).isEqualTo(100L);
      assertThat(result.getScheduler()).isEqualTo(Scheduler.XXL);
      assertThat(result.getSchedulerJobId()).isEqualTo(SCHEDULER_JOB_ID);
      assertThat(result.getSchedulerLogId()).isEqualTo(SCHEDULER_LOG_ID);
      assertThat(result.getTriggerType()).isEqualTo(TriggerType.SCHEDULE);
      assertThat(result.getTriggeredAt()).isEqualTo(TRIGGERED_AT);
      assertThat(result.getProvenanceCode()).isEqualTo(PROVENANCE_CODE);
      assertThat(result.getVersion()).isEqualTo(5L);

      // 验证 triggerParams 转换
      assertThat(result.getTriggerParams()).isNotNull();
      assertThat(result.getTriggerParams()).containsEntry("window", "2025-01");
      assertThat(result.getTriggerParams()).containsEntry("mode", "incremental");
      assertThat(result.getTriggerParams()).containsEntry("retryCount", 3);
    }

    @Test
    @DisplayName("应该正确转换 SPRING + MANUAL 触发类型的 ScheduleInstanceDO")
    void shouldConvertSpringManualDO() {
      // Given: 创建 SPRING + MANUAL 触发类型的 ScheduleInstanceDO
      ScheduleInstanceDO instanceDO = new ScheduleInstanceDO();
      instanceDO.setId(200L);
      instanceDO.setSchedulerCode("SPRING");
      instanceDO.setSchedulerJobId(SCHEDULER_JOB_ID);
      instanceDO.setSchedulerLogId(SCHEDULER_LOG_ID);
      instanceDO.setTriggerTypeCode("MANUAL");
      instanceDO.setTriggeredAt(TRIGGERED_AT);
      instanceDO.setTriggerParams(JsonNodeMappings.mapToJsonNode(TRIGGER_PARAMS));
      instanceDO.setProvenanceCode(PROVENANCE_CODE.getCode());
      instanceDO.setVersion(0L);

      // When: 转换为 Aggregate
      ScheduleInstanceAggregate result = converter.toDomain(instanceDO);

      // Then: 验证枚举字段
      assertThat(result.getScheduler()).isEqualTo(Scheduler.SPRING);
      assertThat(result.getTriggerType()).isEqualTo(TriggerType.MANUAL);
    }

    @Test
    @DisplayName("应该正确转换 QUARTZ + API 触发类型的 ScheduleInstanceDO")
    void shouldConvertQuartzApiDO() {
      // Given: 创建 QUARTZ + API 触发类型的 ScheduleInstanceDO
      ScheduleInstanceDO instanceDO = new ScheduleInstanceDO();
      instanceDO.setId(300L);
      instanceDO.setSchedulerCode("QUARTZ");
      instanceDO.setSchedulerJobId(SCHEDULER_JOB_ID);
      instanceDO.setSchedulerLogId(SCHEDULER_LOG_ID);
      instanceDO.setTriggerTypeCode("API");
      instanceDO.setTriggeredAt(TRIGGERED_AT);
      instanceDO.setTriggerParams(JsonNodeMappings.mapToJsonNode(TRIGGER_PARAMS));
      instanceDO.setProvenanceCode(PROVENANCE_CODE.getCode());
      instanceDO.setVersion(0L);

      // When: 转换为 Aggregate
      ScheduleInstanceAggregate result = converter.toDomain(instanceDO);

      // Then: 验证枚举字段
      assertThat(result.getScheduler()).isEqualTo(Scheduler.QUARTZ);
      assertThat(result.getTriggerType()).isEqualTo(TriggerType.API);
    }

    @Test
    @DisplayName("应该正确处理 null ScheduleInstanceDO")
    void shouldReturnNullForNullDO() {
      // When: 转换 null DO
      ScheduleInstanceAggregate result = converter.toDomain(null);

      // Then: 应返回 null
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("应该正确处理 null triggerParams JsonNode")
    void shouldHandleNullTriggerParamsJsonNode() {
      // Given: triggerParams 为 null 的 ScheduleInstanceDO
      ScheduleInstanceDO instanceDO = new ScheduleInstanceDO();
      instanceDO.setId(400L);
      instanceDO.setSchedulerCode("XXL");
      instanceDO.setSchedulerJobId(SCHEDULER_JOB_ID);
      instanceDO.setSchedulerLogId(SCHEDULER_LOG_ID);
      instanceDO.setTriggerTypeCode("SCHEDULE");
      instanceDO.setTriggeredAt(TRIGGERED_AT);
      instanceDO.setTriggerParams(null); // null
      instanceDO.setProvenanceCode(PROVENANCE_CODE.getCode());
      instanceDO.setVersion(0L);

      // When: 转换为 Aggregate
      ScheduleInstanceAggregate result = converter.toDomain(instanceDO);

      // Then: triggerParams 应该为 null 或空 Map
      if (result.getTriggerParams() != null) {
        assertThat(result.getTriggerParams()).isEmpty();
      }
    }
  }

  // ========== 静态辅助方法测试 ==========

  @Nested
  @DisplayName("静态辅助方法测试")
  class StaticHelperMethodTests {

    @Test
    @DisplayName("schedulerToCode() 应正确转换枚举")
    void shouldConvertSchedulerToCode() {
      assertThat(ScheduleInstanceConverter.schedulerToCode(Scheduler.XXL)).isEqualTo("XXL");
      assertThat(ScheduleInstanceConverter.schedulerToCode(Scheduler.SPRING)).isEqualTo("SPRING");
      assertThat(ScheduleInstanceConverter.schedulerToCode(Scheduler.QUARTZ)).isEqualTo("QUARTZ");
      assertThat(ScheduleInstanceConverter.schedulerToCode(null)).isNull();
    }

    @Test
    @DisplayName("schedulerFromCode() 应正确转换代码")
    void shouldConvertCodeToScheduler() {
      assertThat(ScheduleInstanceConverter.schedulerFromCode("XXL")).isEqualTo(Scheduler.XXL);
      assertThat(ScheduleInstanceConverter.schedulerFromCode("SPRING")).isEqualTo(Scheduler.SPRING);
      assertThat(ScheduleInstanceConverter.schedulerFromCode("QUARTZ")).isEqualTo(Scheduler.QUARTZ);
      assertThat(ScheduleInstanceConverter.schedulerFromCode(null)).isNull();
    }

    @Test
    @DisplayName("triggerTypeToCode() 应正确转换枚举")
    void shouldConvertTriggerTypeToCode() {
      assertThat(ScheduleInstanceConverter.triggerTypeToCode(TriggerType.SCHEDULE))
          .isEqualTo("SCHEDULE");
      assertThat(ScheduleInstanceConverter.triggerTypeToCode(TriggerType.MANUAL))
          .isEqualTo("MANUAL");
      assertThat(ScheduleInstanceConverter.triggerTypeToCode(TriggerType.API)).isEqualTo("API");
      assertThat(ScheduleInstanceConverter.triggerTypeToCode(null)).isNull();
    }

    @Test
    @DisplayName("triggerTypeFromCode() 应正确转换代码")
    void shouldConvertCodeToTriggerType() {
      assertThat(ScheduleInstanceConverter.triggerTypeFromCode("SCHEDULE"))
          .isEqualTo(TriggerType.SCHEDULE);
      assertThat(ScheduleInstanceConverter.triggerTypeFromCode("MANUAL"))
          .isEqualTo(TriggerType.MANUAL);
      assertThat(ScheduleInstanceConverter.triggerTypeFromCode("API")).isEqualTo(TriggerType.API);
      assertThat(ScheduleInstanceConverter.triggerTypeFromCode(null)).isNull();
    }
  }

  // ========== 双向转换一致性测试 ==========

  @Nested
  @DisplayName("双向转换一致性测试")
  class RoundTripConsistencyTests {

    @Test
    @DisplayName("XXL + SCHEDULE 的 Aggregate 应通过双向转换保持一致")
    void shouldMaintainConsistencyForXxlScheduleRoundTrip() {
      // Given: 原始 Aggregate
      ScheduleInstanceAggregate original =
          ScheduleInstanceAggregate.restore(
              ScheduleInstanceId.of(100L),
              Scheduler.XXL,
              SCHEDULER_JOB_ID,
              SCHEDULER_LOG_ID,
              TriggerType.SCHEDULE,
              TRIGGERED_AT,
              TRIGGER_PARAMS,
              PROVENANCE_CODE,
              5L);

      // When: 双向转换
      ScheduleInstanceDO entity = converter.toDO(original);
      ScheduleInstanceAggregate restored = converter.toDomain(entity);

      // Then: 验证一致性
      assertThat(restored.getId().value()).isEqualTo(original.getId().value());
      assertThat(restored.getScheduler()).isEqualTo(original.getScheduler());
      assertThat(restored.getSchedulerJobId()).isEqualTo(original.getSchedulerJobId());
      assertThat(restored.getSchedulerLogId()).isEqualTo(original.getSchedulerLogId());
      assertThat(restored.getTriggerType()).isEqualTo(original.getTriggerType());
      assertThat(restored.getTriggeredAt()).isEqualTo(original.getTriggeredAt());
      assertThat(restored.getProvenanceCode()).isEqualTo(original.getProvenanceCode());

      // 验证 triggerParams 一致性
      assertThat(restored.getTriggerParams()).containsAllEntriesOf(original.getTriggerParams());
    }

    @Test
    @DisplayName("SPRING + API 的 Aggregate 应通过双向转换保持一致")
    void shouldMaintainConsistencyForSpringApiRoundTrip() {
      // Given: 原始 Aggregate
      ScheduleInstanceAggregate original =
          ScheduleInstanceAggregate.restore(
              ScheduleInstanceId.of(200L),
              Scheduler.SPRING,
              SCHEDULER_JOB_ID,
              SCHEDULER_LOG_ID,
              TriggerType.API,
              TRIGGERED_AT,
              TRIGGER_PARAMS,
              PROVENANCE_CODE,
              10L);

      // When: 双向转换
      ScheduleInstanceDO entity = converter.toDO(original);
      ScheduleInstanceAggregate restored = converter.toDomain(entity);

      // Then: 验证一致性
      assertThat(restored.getScheduler()).isEqualTo(Scheduler.SPRING);
      assertThat(restored.getTriggerType()).isEqualTo(TriggerType.API);
    }
  }
}
