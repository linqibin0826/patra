package com.patra.ingest.infra.persistence.repository;

import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.port.PlanRepository;
import com.patra.ingest.infra.persistence.converter.PlanConverter;
import com.patra.ingest.infra.persistence.entity.PlanDO;
import com.patra.ingest.infra.persistence.mapper.PlanMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// 采集计划（Plan）仓储实现,基于 MyBatis-Plus。
///
/// 实现策略:
///
/// - 使用 {@link PlanDO} 作为持久化模型
///   - 通过 {@link PlanConverter} 进行聚合根与 DO 转换
///   - 根据 ID 是否存在决定 insert 或 update
///   - 依赖 MyBatis-Plus 的 version 字段实现乐观锁
///
/// 日志策略:
///
/// - DEBUG: 记录 insert/update 操作的关键字段(id, planKey)
///   - INFO: 避免高频 CRUD 产生日志噪音
///
/// 线程安全: 无状态单例,通过依赖注入实现线程安全。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Repository
@RequiredArgsConstructor
public class PlanRepositoryMpImpl implements PlanRepository {

  /// Plan Mapper
  private final PlanMapper planMapper;

  /// 聚合根与 DO 转换器
  private final PlanConverter planConverter;

  /// 保存采集计划。
  ///
  /// 根据聚合根 ID 是否存在决定插入或更新。转换为 DO 后再转回聚合根,以确保 version 和自增字段被正确回写。
  ///
  /// @param plan 计划聚合根(必需)
  /// @return 持久化后的聚合根
  @Override
  public PlanAggregate save(PlanAggregate plan) {
    PlanDO entity = planConverter.toEntity(plan);
    if (entity.getId() == null) {
      if (log.isDebugEnabled()) {
        log.debug("plan insert planKey={}", entity.getPlanKey());
      }
      planMapper.insert(entity);
    } else {
      if (log.isDebugEnabled()) {
        log.debug("plan update id={} planKey={}", entity.getId(), entity.getPlanKey());
      }
      planMapper.updateById(entity);
    }
    return planConverter.toAggregate(entity);
  }

  /// 根据计划键查询计划。
  ///
  /// @param planKey 幂等键(为空则返回空)
  /// @return 计划聚合根(可选)
  @Override
  public Optional<PlanAggregate> findByPlanKey(String planKey) {
    if (planKey == null || planKey.isBlank()) {
      return Optional.empty();
    }
    PlanDO entity = planMapper.findByPlanKey(planKey);
    boolean found = entity != null;
    if (log.isDebugEnabled()) {
      log.debug("query plan by planKey={}, found={}", planKey, found);
    }
    return Optional.ofNullable(entity).map(planConverter::toAggregate);
  }

  /// 检查计划键是否已存在。
  ///
  /// @param planKey 幂等键
  /// @return 存在返回 true,否则返回 false
  @Override
  public boolean existsByPlanKey(String planKey) {
    if (planKey == null || planKey.isBlank()) {
      return false;
    }
    return planMapper.countByPlanKey(planKey) > 0;
  }

  /// 根据 ID 查询计划。
  ///
  /// @param planId 计划 ID
  /// @return 计划聚合根(可选)
  @Override
  public Optional<PlanAggregate> findById(Long planId) {
    if (planId == null) {
      return Optional.empty();
    }
    PlanDO entity = planMapper.selectById(planId);
    boolean found = entity != null;
    if (log.isDebugEnabled()) {
      log.debug("query plan by id={}, found={}", planId, found);
    }
    return Optional.ofNullable(entity).map(planConverter::toAggregate);
  }
}
