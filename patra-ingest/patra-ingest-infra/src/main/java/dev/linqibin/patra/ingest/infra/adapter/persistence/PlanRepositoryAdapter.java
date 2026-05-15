package dev.linqibin.patra.ingest.infra.adapter.persistence;

import com.patra.starter.jpa.id.SnowflakeIdGenerator;
import dev.linqibin.patra.ingest.domain.model.aggregate.PlanAggregate;
import dev.linqibin.patra.ingest.domain.port.PlanRepository;
import dev.linqibin.patra.ingest.infra.adapter.persistence.converter.mapper.PlanJpaMapper;
import dev.linqibin.patra.ingest.infra.adapter.persistence.dao.PlanDao;
import dev.linqibin.patra.ingest.infra.adapter.persistence.entity.PlanEntity;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// 采集计划（Plan）仓储实现，基于 JPA。
///
/// 实现策略：
///
/// - 使用 {@link PlanEntity} 作为 JPA 实体
/// - 通过 {@link PlanJpaMapper} 进行聚合根与实体转换
/// - 使用 {@link SnowflakeIdGenerator} 预分配 ID
/// - 依赖 JPA 的 `@Version` 字段实现乐观锁
///
/// 日志策略：
///
/// - DEBUG：记录 insert/update 操作的关键字段（id, planKey）
/// - INFO：避免高频 CRUD 产生日志噪音
///
/// 线程安全：无状态单例，通过依赖注入实现线程安全。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Repository
@RequiredArgsConstructor
public class PlanRepositoryAdapter implements PlanRepository {

  /// Plan JPA Repository
  private final PlanDao planDao;

  /// 聚合根与 JPA 实体转换器
  private final PlanJpaMapper planJpaMapper;

  /// 保存采集计划。
  ///
  /// 根据聚合根 ID 是否存在决定插入或更新。转换为实体后再转回聚合根，以确保 version 被正确回写。
  ///
  /// @param plan 计划聚合根（必需）
  /// @return 持久化后的聚合根
  @Override
  public PlanAggregate save(PlanAggregate plan) {
    PlanEntity entity = planJpaMapper.toEntity(plan);

    if (plan.getId() == null) {
      // 新增：预分配雪花 ID
      entity.setId(SnowflakeIdGenerator.getId());
      if (log.isDebugEnabled()) {
        log.debug("plan insert planKey={}", entity.getPlanKey());
      }
    } else {
      // 更新：使用现有 ID 和 version
      entity.setId(plan.getId().value());
      entity.setVersion(plan.getVersion());
      if (log.isDebugEnabled()) {
        log.debug("plan update id={} planKey={}", entity.getId(), entity.getPlanKey());
      }
    }

    PlanEntity saved = planDao.save(entity);
    return planJpaMapper.toAggregate(saved);
  }

  /// 根据计划键查询计划。
  ///
  /// @param planKey 幂等键（为空则返回空）
  /// @return 计划聚合根（可选）
  @Override
  public Optional<PlanAggregate> findByPlanKey(String planKey) {
    if (planKey == null || planKey.isBlank()) {
      return Optional.empty();
    }
    Optional<PlanEntity> entity = planDao.findByPlanKey(planKey);
    if (log.isDebugEnabled()) {
      log.debug("query plan by planKey={}, found={}", planKey, entity.isPresent());
    }
    return entity.map(planJpaMapper::toAggregate);
  }

  /// 检查计划键是否已存在。
  ///
  /// @param planKey 幂等键
  /// @return 存在返回 true，否则返回 false
  @Override
  public boolean existsByPlanKey(String planKey) {
    if (planKey == null || planKey.isBlank()) {
      return false;
    }
    return planDao.existsByPlanKey(planKey);
  }

  /// 根据 ID 查询计划。
  ///
  /// @param planId 计划 ID
  /// @return 计划聚合根（可选）
  @Override
  public Optional<PlanAggregate> findById(Long planId) {
    if (planId == null) {
      return Optional.empty();
    }
    Optional<PlanEntity> entity = planDao.findById(planId);
    if (log.isDebugEnabled()) {
      log.debug("query plan by id={}, found={}", planId, entity.isPresent());
    }
    return entity.map(planJpaMapper::toAggregate);
  }
}
