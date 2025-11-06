package com.patra.ingest.infra.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.infra.persistence.converter.PlanConverter;
import com.patra.ingest.infra.persistence.entity.PlanDO;
import com.patra.ingest.infra.persistence.mapper.PlanMapper;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * PlanRepositoryMpImpl 单元测试。
 *
 * <p>测试策略：
 *
 * <ul>
 *   <li>使用 Mockito Mock 所有依赖（Mapper, Converter）
 *   <li>不启动 Spring 容器，纯单元测试
 *   <li>验证方法调用、参数传递和返回值转换
 * </ul>
 *
 * <p>覆盖场景：
 *
 * <ul>
 *   <li>save() - insert 分支
 *   <li>save() - update 分支
 *   <li>findByPlanKey() - 找到记录
 *   <li>findByPlanKey() - 未找到记录
 *   <li>findByPlanKey() - null 或空白参数
 *   <li>existsByPlanKey() - 存在
 *   <li>existsByPlanKey() - 不存在
 *   <li>existsByPlanKey() - null 或空白参数
 *   <li>findById() - 找到记录
 *   <li>findById() - 未找到记录
 *   <li>findById() - null 参数
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PlanRepositoryMpImpl 单元测试")
class PlanRepositoryMpImplTest {

  @Mock private PlanMapper planMapper;
  @Mock private PlanConverter planConverter;

  @InjectMocks private PlanRepositoryMpImpl repository;

  private static final Long TEST_PLAN_ID = 1L;
  private static final String TEST_PLAN_KEY = "plan-001";
  private static final Long TEST_VERSION = 1L;

  @Nested
  @DisplayName("保存操作")
  class SaveTests {

    @Test
    @DisplayName("应在 ID 为 null 时插入新计划")
    void shouldInsertWhenIdIsNull() {
      // Given: 创建无 ID 的聚合根和 DO
      PlanAggregate aggregate = createTestPlanAggregate(null);
      PlanDO entityWithoutId = createTestPlanDO(null);

      when(planConverter.toEntity(aggregate)).thenReturn(entityWithoutId);
      // 模拟插入后 ID 被回写
      when(planMapper.insert(entityWithoutId)).thenAnswer(invocation -> {
        entityWithoutId.setId(TEST_PLAN_ID);
        return 1;
      });
      when(planConverter.toAggregate(entityWithoutId)).thenReturn(aggregate);

      // When: 保存
      PlanAggregate result = repository.save(aggregate);

      // Then: 验证调用了 insert 并返回转换后的聚合根
      assertThat(result).isNotNull();
      verify(planConverter).toEntity(aggregate);
      verify(planMapper).insert(entityWithoutId);
      verify(planConverter).toAggregate(entityWithoutId);
    }

    @Test
    @DisplayName("应在 ID 存在时更新计划")
    void shouldUpdateWhenIdExists() {
      // Given: 创建有 ID 的聚合根和 DO
      PlanAggregate aggregate = createTestPlanAggregate(TEST_PLAN_ID);
      PlanDO entity = createTestPlanDO(TEST_PLAN_ID);

      when(planConverter.toEntity(aggregate)).thenReturn(entity);
      when(planMapper.updateById(entity)).thenReturn(1);
      when(planConverter.toAggregate(entity)).thenReturn(aggregate);

      // When: 保存
      PlanAggregate result = repository.save(aggregate);

      // Then: 验证调用了 updateById
      assertThat(result).isNotNull();
      verify(planConverter).toEntity(aggregate);
      verify(planMapper).updateById(entity);
      verify(planConverter).toAggregate(entity);
    }

    @Test
    @DisplayName("应在保存后返回转换后的聚合根")
    void shouldReturnConvertedAggregateAfterSave() {
      // Given
      PlanAggregate aggregate = createTestPlanAggregate(null);
      PlanDO entity = createTestPlanDO(null);
      PlanAggregate convertedAggregate = createTestPlanAggregate(TEST_PLAN_ID);

      when(planConverter.toEntity(aggregate)).thenReturn(entity);
      when(planMapper.insert(entity)).thenReturn(1);
      entity.setId(TEST_PLAN_ID);
      when(planConverter.toAggregate(entity)).thenReturn(convertedAggregate);

      // When
      PlanAggregate result = repository.save(aggregate);

      // Then: 验证返回的是转换后的聚合根
      assertThat(result).isSameAs(convertedAggregate);
    }
  }

  @Nested
  @DisplayName("按 PlanKey 查询")
  class FindByPlanKeyTests {

    @Test
    @DisplayName("应在 planKey 存在时返回计划")
    void shouldReturnPlanWhenPlanKeyExists() {
      // Given
      PlanDO entity = createTestPlanDO(TEST_PLAN_ID);
      PlanAggregate aggregate = createTestPlanAggregate(TEST_PLAN_ID);

      when(planMapper.findByPlanKey(TEST_PLAN_KEY)).thenReturn(entity);
      when(planConverter.toAggregate(entity)).thenReturn(aggregate);

      // When
      Optional<PlanAggregate> result = repository.findByPlanKey(TEST_PLAN_KEY);

      // Then
      assertThat(result).isPresent().contains(aggregate);
      verify(planMapper).findByPlanKey(TEST_PLAN_KEY);
      verify(planConverter).toAggregate(entity);
    }

    @Test
    @DisplayName("应在 planKey 不存在时返回空 Optional")
    void shouldReturnEmptyWhenPlanKeyNotFound() {
      // Given
      when(planMapper.findByPlanKey(TEST_PLAN_KEY)).thenReturn(null);

      // When
      Optional<PlanAggregate> result = repository.findByPlanKey(TEST_PLAN_KEY);

      // Then
      assertThat(result).isEmpty();
      verify(planMapper).findByPlanKey(TEST_PLAN_KEY);
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
      when(planMapper.countByPlanKey(TEST_PLAN_KEY)).thenReturn(1);

      // When
      boolean result = repository.existsByPlanKey(TEST_PLAN_KEY);

      // Then
      assertThat(result).isTrue();
      verify(planMapper).countByPlanKey(TEST_PLAN_KEY);
    }

    @Test
    @DisplayName("应在 planKey 不存在时返回 false")
    void shouldReturnFalseWhenPlanKeyNotFound() {
      // Given
      when(planMapper.countByPlanKey(TEST_PLAN_KEY)).thenReturn(0);

      // When
      boolean result = repository.existsByPlanKey(TEST_PLAN_KEY);

      // Then
      assertThat(result).isFalse();
      verify(planMapper).countByPlanKey(TEST_PLAN_KEY);
    }

    @Test
    @DisplayName("应在 planKey 为 null 时返回 false")
    void shouldReturnFalseWhenPlanKeyIsNull() {
      // When
      boolean result = repository.existsByPlanKey(null);

      // Then
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("应在 planKey 为空白字符串时返回 false")
    void shouldReturnFalseWhenPlanKeyIsBlank() {
      // When
      boolean result = repository.existsByPlanKey("   ");

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
      PlanDO entity = createTestPlanDO(TEST_PLAN_ID);
      PlanAggregate aggregate = createTestPlanAggregate(TEST_PLAN_ID);

      when(planMapper.selectById(TEST_PLAN_ID)).thenReturn(entity);
      when(planConverter.toAggregate(entity)).thenReturn(aggregate);

      // When
      Optional<PlanAggregate> result = repository.findById(TEST_PLAN_ID);

      // Then
      assertThat(result).isPresent().contains(aggregate);
      verify(planMapper).selectById(TEST_PLAN_ID);
      verify(planConverter).toAggregate(entity);
    }

    @Test
    @DisplayName("应在 ID 不存在时返回空 Optional")
    void shouldReturnEmptyWhenIdNotFound() {
      // Given
      when(planMapper.selectById(TEST_PLAN_ID)).thenReturn(null);

      // When
      Optional<PlanAggregate> result = repository.findById(TEST_PLAN_ID);

      // Then
      assertThat(result).isEmpty();
      verify(planMapper).selectById(TEST_PLAN_ID);
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

  private PlanAggregate createTestPlanAggregate(Long id) {
    PlanAggregate aggregate = org.mockito.Mockito.mock(PlanAggregate.class);
    when(aggregate.getId()).thenReturn(id);
    when(aggregate.getPlanKey()).thenReturn(TEST_PLAN_KEY);
    when(aggregate.getVersion()).thenReturn(TEST_VERSION);
    return aggregate;
  }

  private PlanDO createTestPlanDO(Long id) {
    PlanDO entity = new PlanDO();
    entity.setId(id);
    entity.setPlanKey(TEST_PLAN_KEY);
    entity.setVersion(TEST_VERSION);
    return entity;
  }
}
