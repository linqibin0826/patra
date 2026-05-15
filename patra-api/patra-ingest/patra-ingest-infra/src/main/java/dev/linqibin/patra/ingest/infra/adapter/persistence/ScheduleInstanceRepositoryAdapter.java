package dev.linqibin.patra.ingest.infra.adapter.persistence;

import cn.hutool.core.lang.Assert;
import dev.linqibin.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;
import dev.linqibin.patra.ingest.domain.port.ScheduleInstanceRepository;
import dev.linqibin.patra.ingest.infra.adapter.persistence.converter.mapper.ScheduleInstanceJpaMapper;
import dev.linqibin.patra.ingest.infra.adapter.persistence.dao.ScheduleInstanceDao;
import dev.linqibin.patra.ingest.infra.adapter.persistence.entity.ScheduleInstanceEntity;
import com.patra.starter.jpa.id.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// 调度实例（ScheduleInstance）仓储实现，基于 JPA。
///
/// 职责：
///
/// - 根据 ID 是否存在决定插入或更新
/// - 不验证调度语义（如触发类型合法性），委托给领域层
/// - 幂等性：调用方确保不会创建重复的逻辑实例
///
/// 日志策略：DEBUG 级别记录 insert/update 的关键字段；不输出 INFO 日志。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Repository
@RequiredArgsConstructor
public class ScheduleInstanceRepositoryAdapter implements ScheduleInstanceRepository {

  /// ScheduleInstance JPA Repository
  private final ScheduleInstanceDao scheduleInstanceDao;

  /// 聚合根与 JPA 实体转换器
  private final ScheduleInstanceJpaMapper scheduleInstanceJpaMapper;

  /// 保存或更新调度实例。
  ///
  /// @param instance 调度实例聚合根
  /// @return 持久化后的聚合根（插入时返回转换后的新实例）
  @Override
  public ScheduleInstanceAggregate saveOrUpdateInstance(ScheduleInstanceAggregate instance) {
    Assert.notNull(instance, "ScheduleInstanceAggregate cannot be null");

    ScheduleInstanceEntity entity = scheduleInstanceJpaMapper.toEntity(instance);

    if (instance.getId() == null) {
      // 新增：预分配雪花 ID
      entity.setId(SnowflakeIdGenerator.getId());
      if (log.isDebugEnabled()) {
        log.debug(
            "schedule instance insert triggeredAt={} triggerType={}",
            entity.getTriggeredAt(),
            instance.getTriggerType());
      }
    } else {
      // 更新：ValueObjectJpaEntity 无需乐观锁
      entity.setId(instance.getId().value());
      if (log.isDebugEnabled()) {
        log.debug(
            "schedule instance update id={} triggerType={}",
            instance.getId(),
            instance.getTriggerType());
      }
    }

    ScheduleInstanceEntity saved = scheduleInstanceDao.save(entity);
    return scheduleInstanceJpaMapper.toAggregate(saved);
  }
}
