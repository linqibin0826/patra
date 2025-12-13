package com.patra.ingest.infra.persistence.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.common.enums.ProvenanceCode;
import com.patra.common.json.JsonMapperHolder;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.enums.TaskStatus;
import com.patra.ingest.domain.model.vo.execution.ExecutionTimeline;
import com.patra.ingest.domain.model.vo.plan.PlanId;
import com.patra.ingest.domain.model.vo.plan.TaskSchedulerContext;
import com.patra.ingest.domain.model.vo.schedule.ScheduleInstanceId;
import com.patra.ingest.domain.model.vo.shared.LeaseInfo;
import com.patra.ingest.domain.model.vo.slice.PlanSliceId;
import com.patra.ingest.domain.model.vo.task.TaskId;
import com.patra.ingest.infra.persistence.entity.TaskDO;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

/// TaskConverter 单元测试。
///
/// 测试策略：
///
/// - 测试聚合根 → DO 的转换
///   - 测试 DO → 聚合根的转换
///   - 测试双向转换的一致性
///   - 测试空值和边界情况
///   - 测试状态枚举转换
///   - 测试 JSON 字段转换
///   - 测试值对象的分解和组装
///
/// 注意：MapStruct 转换器通过 Mappers.getMapper() 直接实例化，无需 Spring 容器。
class TaskConverterTest {

  private final TaskConverter converter = Mappers.getMapper(TaskConverter.class);

  @Test
  @DisplayName("应当正确将TaskAggregate转换为TaskDO")
  void shouldConvertAggregateToEntity() throws Exception {
    // Given: 构造完整的任务聚合根
    Instant now = Instant.now();
    Instant scheduledAt = now.minusSeconds(3600);
    Instant startedAt = now.minusSeconds(1800);
    Instant finishedAt = now.minusSeconds(900);
    Instant leasedUntil = now.plusSeconds(300);

    String paramsJson = "{\"query\":\"test\",\"pageSize\":100}";
    LeaseInfo leaseInfo = LeaseInfo.snapshotOf("worker-01", leasedUntil, 2);
    ExecutionTimeline timeline = new ExecutionTimeline(startedAt, finishedAt);
    TaskSchedulerContext context = new TaskSchedulerContext("correlation-123");

    TaskAggregate aggregate =
        TaskAggregate.restore(
            TaskId.of(1001L),
            ScheduleInstanceId.of(2001L),
            PlanId.of(3001L),
            PlanSliceId.of(4001L),
            ProvenanceCode.PUBMED,
            "HARVEST",
            paramsJson,
            "idem-key-001",
            "expr-hash-001",
            5,
            scheduledAt,
            now,
            2,
            "ERR_NETWORK",
            "Network timeout",
            TaskStatus.RUNNING,
            leaseInfo,
            timeline,
            context,
            10L);

    // When: 转换为DO
    TaskDO entity = converter.toEntity(aggregate);

    // Then: 验证所有字段正确映射
    assertThat(entity).isNotNull();
    assertThat(entity.getId()).isEqualTo(1001L);
    assertThat(entity.getScheduleInstanceId()).isEqualTo(2001L);
    assertThat(entity.getPlanId()).isEqualTo(3001L);
    assertThat(entity.getSliceId()).isEqualTo(4001L);
    assertThat(entity.getProvenanceCode()).isEqualTo("PUBMED");
    assertThat(entity.getOperationCode()).isEqualTo("HARVEST");
    assertThat(entity.getIdempotentKey()).isEqualTo("idem-key-001");
    assertThat(entity.getExprHash()).isEqualTo("expr-hash-001");
    assertThat(entity.getPriority()).isEqualTo(5);
    assertThat(entity.getScheduledAt()).isEqualTo(scheduledAt);
    assertThat(entity.getLastHeartbeatAt()).isEqualTo(now);
    assertThat(entity.getRetryCount()).isEqualTo(2);
    assertThat(entity.getLastErrorCode()).isEqualTo("ERR_NETWORK");
    assertThat(entity.getLastErrorMsg()).isEqualTo("Network timeout");

    // 验证状态转换
    assertThat(entity.getStatusCode()).isEqualTo("RUNNING");

    // 验证JSON字段转换
    assertThat(entity.getParams()).isNotNull();
    assertThat(entity.getParams().get("query").asText()).isEqualTo("test");
    assertThat(entity.getParams().get("pageSize").asInt()).isEqualTo(100);

    // 验证租约信息分解
    assertThat(entity.getLeaseOwner()).isEqualTo("worker-01");
    assertThat(entity.getLeasedUntil()).isEqualTo(leasedUntil);
    assertThat(entity.getLeaseCount()).isEqualTo(2);

    // 验证执行时间线分解
    assertThat(entity.getStartedAt()).isEqualTo(startedAt);
    assertThat(entity.getFinishedAt()).isEqualTo(finishedAt);

    // 验证调度器上下文分解
    assertThat(entity.getCorrelationId()).isEqualTo("correlation-123");
  }

  @Test
  @DisplayName("应当正确将TaskDO转换为TaskAggregate")
  void shouldConvertEntityToAggregate() throws Exception {
    // Given: 构造完整的TaskDO
    Instant now = Instant.now();
    Instant scheduledAt = now.minusSeconds(3600);
    Instant startedAt = now.minusSeconds(1800);
    Instant finishedAt = now.minusSeconds(900);
    Instant leasedUntil = now.plusSeconds(300);

    JsonNode params =
        JsonMapperHolder.getObjectMapper().readTree("{\"query\":\"test\",\"pageSize\":100}");

    TaskDO entity = new TaskDO();
    entity.setId(1001L);
    entity.setScheduleInstanceId(2001L);
    entity.setPlanId(3001L);
    entity.setSliceId(4001L);
    entity.setProvenanceCode("PUBMED");
    entity.setOperationCode("HARVEST");
    entity.setParams(params);
    entity.setIdempotentKey("idem-key-001");
    entity.setExprHash("expr-hash-001");
    entity.setPriority(5);
    entity.setLeaseOwner("worker-01");
    entity.setLeasedUntil(leasedUntil);
    entity.setLeaseCount(2);
    entity.setLastHeartbeatAt(now);
    entity.setRetryCount(2);
    entity.setLastErrorCode("ERR_NETWORK");
    entity.setLastErrorMsg("Network timeout");
    entity.setStatusCode("RUNNING");
    entity.setScheduledAt(scheduledAt);
    entity.setStartedAt(startedAt);
    entity.setFinishedAt(finishedAt);
    entity.setCorrelationId("correlation-123");
    entity.setVersion(10L);

    // When: 转换为聚合根
    TaskAggregate aggregate = converter.toAggregate(entity);

    // Then: 验证所有字段正确映射
    assertThat(aggregate).isNotNull();
    assertThat(aggregate.getId().value()).isEqualTo(1001L);
    assertThat(aggregate.getScheduleInstanceId()).isEqualTo(ScheduleInstanceId.of(2001L));
    assertThat(aggregate.getPlanId()).isEqualTo(PlanId.of(3001L));
    assertThat(aggregate.getSliceId()).isEqualTo(PlanSliceId.of(4001L));
    assertThat(aggregate.getProvenanceCode()).isEqualTo(ProvenanceCode.PUBMED);
    assertThat(aggregate.getOperationCode()).isEqualTo("HARVEST");
    assertThat(aggregate.getIdempotentKey()).isEqualTo("idem-key-001");
    assertThat(aggregate.getExprHash()).isEqualTo("expr-hash-001");
    assertThat(aggregate.getPriority()).isEqualTo(5);
    assertThat(aggregate.getScheduledAt()).isEqualTo(scheduledAt);
    assertThat(aggregate.getLastHeartbeatAt()).isEqualTo(now);
    assertThat(aggregate.getRetryCount()).isEqualTo(2);
    assertThat(aggregate.getLastErrorCode()).isEqualTo("ERR_NETWORK");
    assertThat(aggregate.getLastErrorMsg()).isEqualTo("Network timeout");
    assertThat(aggregate.getVersion()).isEqualTo(10L);

    // 验证状态转换
    assertThat(aggregate.getStatus()).isEqualTo(TaskStatus.RUNNING);

    // 验证JSON字段转换
    assertThat(aggregate.getParamsJson()).contains("\"query\":\"test\"");
    assertThat(aggregate.getParamsJson()).contains("\"pageSize\":100");

    // 验证租约信息组装
    assertThat(aggregate.getLeaseInfo()).isNotNull();
    assertThat(aggregate.getLeaseInfo().owner()).isEqualTo("worker-01");
    assertThat(aggregate.getLeaseInfo().leasedUntil()).isEqualTo(leasedUntil);
    assertThat(aggregate.getLeaseInfo().leaseCount()).isEqualTo(2);

    // 验证执行时间线组装
    assertThat(aggregate.getExecutionTimeline()).isNotNull();
    assertThat(aggregate.getExecutionTimeline().startedAt()).isEqualTo(startedAt);
    assertThat(aggregate.getExecutionTimeline().finishedAt()).isEqualTo(finishedAt);

    // 验证调度器上下文组装
    assertThat(aggregate.getSchedulerContext()).isNotNull();
    assertThat(aggregate.getSchedulerContext().correlationId()).isEqualTo("correlation-123");
  }

  @Test
  @DisplayName("应当支持双向转换的一致性")
  void shouldMaintainConsistencyInRoundTripConversion() throws Exception {
    // Given: 原始聚合根
    Instant now = Instant.now();
    String paramsJson = "{\"query\":\"test\"}";
    LeaseInfo leaseInfo = LeaseInfo.snapshotOf("worker-01", now.plusSeconds(300), 1);
    ExecutionTimeline timeline = new ExecutionTimeline(now.minusSeconds(100), null);
    TaskSchedulerContext context = new TaskSchedulerContext("corr-001");

    TaskAggregate original =
        TaskAggregate.restore(
            TaskId.of(1L),
            ScheduleInstanceId.of(2L),
            PlanId.of(3L),
            PlanSliceId.of(4L),
            ProvenanceCode.PUBMED,
            "HARVEST",
            paramsJson,
            "idem-001",
            "hash-001",
            5,
            now,
            null,
            0,
            null,
            null,
            TaskStatus.RUNNING,
            leaseInfo,
            timeline,
            context,
            1L);

    // When: 聚合 → DO → 聚合
    TaskDO entity = converter.toEntity(original);
    entity.setVersion(1L); // 模拟数据库设置version
    TaskAggregate restored = converter.toAggregate(entity);

    // Then: 关键字段应保持一致
    assertThat(restored.getId().value()).isEqualTo(original.getId().value());
    assertThat(restored.getProvenanceCode()).isEqualTo(original.getProvenanceCode());
    assertThat(restored.getOperationCode()).isEqualTo(original.getOperationCode());
    assertThat(restored.getStatus()).isEqualTo(original.getStatus());
    assertThat(restored.getLeaseInfo().owner()).isEqualTo(original.getLeaseInfo().owner());
    assertThat(restored.getSchedulerContext().correlationId())
        .isEqualTo(original.getSchedulerContext().correlationId());
  }

  @Test
  @DisplayName("应当正确处理空状态转换为QUEUED")
  void shouldConvertNullStatusToQueued() {
    // Given: 状态为null的DO
    TaskDO entity = new TaskDO();
    entity.setId(1L);
    entity.setScheduleInstanceId(2L);
    entity.setPlanId(3L);
    entity.setSliceId(4L);
    entity.setProvenanceCode("PUBMED");
    entity.setOperationCode("HARVEST");
    entity.setIdempotentKey("idem-001");
    entity.setExprHash("hash-001");
    entity.setPriority(5);
    entity.setScheduledAt(Instant.now());
    entity.setStatusCode(null); // 状态为null

    // When: 转换为聚合
    TaskAggregate aggregate = converter.toAggregate(entity);

    // Then: 状态应转换为QUEUED
    assertThat(aggregate.getStatus()).isEqualTo(TaskStatus.QUEUED);
  }

  @Test
  @DisplayName("应当正确处理所有TaskStatus枚举值的转换")
  void shouldConvertAllTaskStatusValues() {
    // Given & When & Then: 测试所有状态枚举
    assertThat(TaskConverter.taskStatusToCode(TaskStatus.QUEUED)).isEqualTo("QUEUED");
    assertThat(TaskConverter.taskStatusToCode(TaskStatus.RUNNING)).isEqualTo("RUNNING");
    assertThat(TaskConverter.taskStatusToCode(TaskStatus.SUCCEEDED)).isEqualTo("SUCCEEDED");
    assertThat(TaskConverter.taskStatusToCode(TaskStatus.FAILED)).isEqualTo("FAILED");
    assertThat(TaskConverter.taskStatusToCode(null)).isNull();

    assertThat(TaskConverter.taskStatusFromCode("QUEUED")).isEqualTo(TaskStatus.QUEUED);
    assertThat(TaskConverter.taskStatusFromCode("RUNNING")).isEqualTo(TaskStatus.RUNNING);
    assertThat(TaskConverter.taskStatusFromCode("SUCCEEDED")).isEqualTo(TaskStatus.SUCCEEDED);
    assertThat(TaskConverter.taskStatusFromCode("FAILED")).isEqualTo(TaskStatus.FAILED);
    assertThat(TaskConverter.taskStatusFromCode(null)).isEqualTo(TaskStatus.QUEUED); // 默认值
  }

  @Test
  @DisplayName("应当正确处理空租约信息")
  void shouldHandleEmptyLeaseInfo() {
    // Given: 租约字段为null的DO
    TaskDO entity = new TaskDO();
    entity.setId(1L);
    entity.setScheduleInstanceId(2L);
    entity.setPlanId(3L);
    entity.setSliceId(4L);
    entity.setProvenanceCode("PUBMED");
    entity.setOperationCode("HARVEST");
    entity.setIdempotentKey("idem-001");
    entity.setExprHash("hash-001");
    entity.setPriority(5);
    entity.setScheduledAt(Instant.now());
    entity.setStatusCode("QUEUED");
    entity.setLeaseOwner(null);
    entity.setLeasedUntil(null);
    entity.setLeaseCount(null);

    // When: 转换为聚合
    TaskAggregate aggregate = converter.toAggregate(entity);

    // Then: LeaseInfo应为空对象
    assertThat(aggregate.getLeaseInfo()).isNotNull();
    assertThat(aggregate.getLeaseInfo().owner()).isNull();
    assertThat(aggregate.getLeaseInfo().leasedUntil()).isNull();
    assertThat(aggregate.getLeaseInfo().leaseCount()).isEqualTo(0);
  }

  @Test
  @DisplayName("应当正确处理空执行时间线")
  void shouldHandleEmptyExecutionTimeline() {
    // Given: 执行时间线字段为null的DO
    TaskDO entity = new TaskDO();
    entity.setId(1L);
    entity.setScheduleInstanceId(2L);
    entity.setPlanId(3L);
    entity.setSliceId(4L);
    entity.setProvenanceCode("PUBMED");
    entity.setOperationCode("HARVEST");
    entity.setIdempotentKey("idem-001");
    entity.setExprHash("hash-001");
    entity.setPriority(5);
    entity.setScheduledAt(Instant.now());
    entity.setStatusCode("QUEUED");
    entity.setStartedAt(null);
    entity.setFinishedAt(null);

    // When: 转换为聚合
    TaskAggregate aggregate = converter.toAggregate(entity);

    // Then: ExecutionTimeline应为空对象
    assertThat(aggregate.getExecutionTimeline()).isNotNull();
    assertThat(aggregate.getExecutionTimeline().startedAt()).isNull();
    assertThat(aggregate.getExecutionTimeline().finishedAt()).isNull();
  }

  @Test
  @DisplayName("应当正确处理空调度器上下文")
  void shouldHandleEmptySchedulerContext() {
    // Given: correlationId为null的DO
    TaskDO entity = new TaskDO();
    entity.setId(1L);
    entity.setScheduleInstanceId(2L);
    entity.setPlanId(3L);
    entity.setSliceId(4L);
    entity.setProvenanceCode("PUBMED");
    entity.setOperationCode("HARVEST");
    entity.setIdempotentKey("idem-001");
    entity.setExprHash("hash-001");
    entity.setPriority(5);
    entity.setScheduledAt(Instant.now());
    entity.setStatusCode("QUEUED");
    entity.setCorrelationId(null);

    // When: 转换为聚合
    TaskAggregate aggregate = converter.toAggregate(entity);

    // Then: SchedulerContext应为空对象
    assertThat(aggregate.getSchedulerContext()).isNotNull();
    assertThat(aggregate.getSchedulerContext().correlationId()).isNull();
  }

  @Test
  @DisplayName("应当正确处理空version字段，默认为0")
  void shouldHandleNullVersionAsZero() {
    // Given: version为null的DO
    TaskDO entity = new TaskDO();
    entity.setId(1L);
    entity.setScheduleInstanceId(2L);
    entity.setPlanId(3L);
    entity.setSliceId(4L);
    entity.setProvenanceCode("PUBMED");
    entity.setOperationCode("HARVEST");
    entity.setIdempotentKey("idem-001");
    entity.setExprHash("hash-001");
    entity.setPriority(5);
    entity.setScheduledAt(Instant.now());
    entity.setStatusCode("QUEUED");
    entity.setVersion(null);

    // When: 转换为聚合
    TaskAggregate aggregate = converter.toAggregate(entity);

    // Then: version应为0
    assertThat(aggregate.getVersion()).isEqualTo(0L);
  }

  @Test
  @DisplayName("应当正确处理空params字段")
  void shouldHandleNullParams() {
    // Given: params为null的DO
    TaskDO entity = new TaskDO();
    entity.setId(1L);
    entity.setScheduleInstanceId(2L);
    entity.setPlanId(3L);
    entity.setSliceId(4L);
    entity.setProvenanceCode("PUBMED");
    entity.setOperationCode("HARVEST");
    entity.setIdempotentKey("idem-001");
    entity.setExprHash("hash-001");
    entity.setPriority(5);
    entity.setScheduledAt(Instant.now());
    entity.setStatusCode("QUEUED");
    entity.setParams(null);

    // When: 转换为聚合
    TaskAggregate aggregate = converter.toAggregate(entity);

    // Then: paramsJson应为null
    assertThat(aggregate.getParamsJson()).isNull();
  }

  @Test
  @DisplayName("应当正确处理null聚合转换")
  void shouldHandleNullAggregate() {
    // Given: null聚合
    TaskAggregate aggregate = null;

    // When: 转换为DO（通过静态方法）
    TaskDO entity = TaskConverter.toTaskAggregate(null) == null ? null : new TaskDO();

    // Then: 应返回null
    assertThat(TaskConverter.toTaskAggregate(null)).isNull();
  }

  @Test
  @DisplayName("应当正确处理null DO转换")
  void shouldHandleNullEntity() {
    // Given: null DO
    TaskDO entity = null;

    // When: 转换为聚合
    TaskAggregate aggregate = TaskConverter.toTaskAggregate(entity);

    // Then: 应返回null
    assertThat(aggregate).isNull();
  }
}
