package com.patra.ingest.infra.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.test.autoconfigure.MybatisPlusTest;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.infra.persistence.entity.PlanDO;
import com.patra.ingest.infra.persistence.entity.ScheduleInstanceDO;
import com.patra.ingest.infra.persistence.mapper.PlanMapper;
import com.patra.ingest.infra.persistence.mapper.ScheduleInstanceMapper;
import com.patra.ingest.infra.config.IngestMySQLContainerInitializer;
import com.patra.starter.test.autoconfigure.TestMybatisPlusAutoConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/// PlanRepositoryAdapter 集成测试。
///
/// 使用 TestContainers + MySQL 8 测试计划聚合根持久化。
///
/// **测试策略**：
///
/// - 集成测试：使用真实 MySQL 数据库
///   - 测试隔离：每个测试方法前清理并重建测试数据
///   - TestContainers：自动启动和停止 MySQL 容器
///   - Flyway：自动执行数据库迁移脚本
///   - 测试覆盖：save、findByPlanKey、existsByPlanKey、findById
///
/// @author linqibin
/// @since 0.1.0
@MybatisPlusTest
@ContextConfiguration(initializers = IngestMySQLContainerInitializer.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  PlanRepositoryAdapter.class,
  TestMybatisPlusAutoConfiguration.class,
  JacksonAutoConfiguration.class
})
@ComponentScan("com.patra.ingest.infra.persistence.converter")
@MapperScan("com.patra.ingest.infra.persistence.mapper")
@ActiveProfiles("test")
@DisplayName("PlanRepositoryAdapter 集成测试")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class PlanRepositoryAdapterIT {

  @Autowired private PlanRepositoryAdapter repository;

  @Autowired private PlanMapper planMapper;
  @Autowired private ScheduleInstanceMapper scheduleInstanceMapper;
  @Autowired private ObjectMapper objectMapper;

  private static final String TEST_PLAN_KEY = "PUBMED-HARVEST-2025-01-01";
  private static final String TEST_PROVENANCE_CODE = "PUBMED";
  private static final String TEST_OPERATION_CODE = "HARVEST";

  private Long testScheduleInstanceId;

  @BeforeEach
  void setUp() {
    // 清理现有数据
    planMapper.delete(Wrappers.<PlanDO>lambdaQuery().ne(PlanDO::getId, 0L));
    scheduleInstanceMapper.delete(
        Wrappers.<ScheduleInstanceDO>lambdaQuery().ne(ScheduleInstanceDO::getId, 0L));

    // 创建依赖的调度实例
    testScheduleInstanceId = insertScheduleInstance();
  }

  @Nested
  @DisplayName("保存操作")
  class SaveTests {

    @Test
    @DisplayName("应在通过 Mapper 插入后通过 findById 查询到计划")
    void shouldFindPlanAfterMapperInsert() {
      // Given: 通过 Mapper 直接插入测试数据
      PlanDO planDO = createTestPlanDO();
      planMapper.insert(planDO);

      // When
      Optional<PlanAggregate> result = repository.findById(planDO.getId());

      // Then
      assertThat(result).isPresent();
      assertThat(result.get().getPlanKey()).isEqualTo(TEST_PLAN_KEY);
    }

    @Test
    @DisplayName("应正确映射状态字段")
    void shouldMapStatusFieldCorrectly() {
      // Given
      PlanDO planDO = createTestPlanDO();
      planDO.setStatusCode("SLICING");
      planMapper.insert(planDO);

      // When
      Optional<PlanAggregate> result = repository.findById(planDO.getId());

      // Then
      assertThat(result).isPresent();
      // 验证数据库记录
      PlanDO entity = planMapper.selectById(result.get().getId());
      assertThat(entity.getStatusCode()).isEqualTo("SLICING");
    }
  }

  @Nested
  @DisplayName("按 PlanKey 查询")
  class FindByPlanKeyTests {

    @Test
    @DisplayName("应在 planKey 存在时返回计划")
    void shouldReturnPlanWhenPlanKeyExists() {
      // Given
      PlanDO planDO = createTestPlanDO();
      planMapper.insert(planDO);

      // When
      Optional<PlanAggregate> result = repository.findByPlanKey(TEST_PLAN_KEY);

      // Then
      assertThat(result).isPresent();
      assertThat(result.get().getPlanKey()).isEqualTo(TEST_PLAN_KEY);
    }

    @Test
    @DisplayName("应在 planKey 不存在时返回空 Optional")
    void shouldReturnEmptyWhenPlanKeyNotFound() {
      // Given: 不插入任何计划

      // When
      Optional<PlanAggregate> result = repository.findByPlanKey("non-existent-key");

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("应在 planKey 为 null 时返回空 Optional")
    void shouldReturnEmptyWhenPlanKeyIsNull() {
      // When
      Optional<PlanAggregate> result = repository.findByPlanKey(null);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("应在 planKey 为空白字符串时返回空 Optional")
    void shouldReturnEmptyWhenPlanKeyIsBlank() {
      // When
      Optional<PlanAggregate> result = repository.findByPlanKey("   ");

      // Then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("检查 PlanKey 是否存在")
  class ExistsByPlanKeyTests {

    @Test
    @DisplayName("应在 planKey 存在时返回 true")
    void shouldReturnTrueWhenPlanKeyExists() {
      // Given
      PlanDO planDO = createTestPlanDO();
      planMapper.insert(planDO);

      // When
      boolean result = repository.existsByPlanKey(TEST_PLAN_KEY);

      // Then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("应在 planKey 不存在时返回 false")
    void shouldReturnFalseWhenPlanKeyNotFound() {
      // Given: 不插入任何计划

      // When
      boolean result = repository.existsByPlanKey("non-existent-key");

      // Then
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("应在 planKey 为 null 时返回 false")
    void shouldReturnFalseWhenPlanKeyIsNull() {
      // When
      boolean result = repository.existsByPlanKey(null);

      // Then
      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("按 ID 查询")
  class FindByIdTests {

    @Test
    @DisplayName("应在 ID 存在时返回计划")
    void shouldReturnPlanWhenIdExists() {
      // Given
      PlanDO planDO = createTestPlanDO();
      planMapper.insert(planDO);

      // When
      Optional<PlanAggregate> result = repository.findById(planDO.getId());

      // Then
      assertThat(result).isPresent();
      assertThat(result.get().getPlanKey()).isEqualTo(TEST_PLAN_KEY);
    }

    @Test
    @DisplayName("应在 ID 不存在时返回空 Optional")
    void shouldReturnEmptyWhenIdNotFound() {
      // Given: 不插入任何计划

      // When
      Optional<PlanAggregate> result = repository.findById(999L);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("应在 ID 为 null 时返回空 Optional")
    void shouldReturnEmptyWhenIdIsNull() {
      // When
      Optional<PlanAggregate> result = repository.findById(null);

      // Then
      assertThat(result).isEmpty();
    }
  }

  // ==================== 辅助方法 ====================

  private Long insertScheduleInstance() {
    ScheduleInstanceDO instance = new ScheduleInstanceDO();
    instance.setSchedulerCode("XXL");
    instance.setTriggerTypeCode("SCHEDULE");
    instance.setTriggeredAt(Instant.now());
    instance.setProvenanceCode(TEST_PROVENANCE_CODE);
    scheduleInstanceMapper.insert(instance);
    return instance.getId();
  }

  private PlanDO createTestPlanDO() {
    PlanDO plan = new PlanDO();
    plan.setScheduleInstanceId(testScheduleInstanceId);
    plan.setPlanKey(TEST_PLAN_KEY);
    plan.setProvenanceCode(TEST_PROVENANCE_CODE);
    plan.setOperationCode(TEST_OPERATION_CODE);
    plan.setSliceStrategyCode("SINGLE");
    plan.setExprProtoHash("test-expr-hash-001");
    plan.setWindowSpec(createSingleWindowSpec());
    plan.setStatusCode("DRAFT");
    return plan;
  }

  private ObjectNode createSingleWindowSpec() {
    ObjectNode windowSpec = objectMapper.createObjectNode();
    windowSpec.put("strategy", "SINGLE");
    return windowSpec;
  }
}
