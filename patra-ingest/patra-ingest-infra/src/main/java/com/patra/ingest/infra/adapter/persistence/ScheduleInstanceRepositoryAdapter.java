package com.patra.ingest.infra.adapter.persistence;

import cn.hutool.core.lang.Assert;
import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;
import com.patra.ingest.domain.port.ScheduleInstanceRepository;
import com.patra.ingest.infra.persistence.converter.ScheduleInstanceConverter;
import com.patra.ingest.infra.persistence.entity.ScheduleInstanceDO;
import com.patra.ingest.infra.persistence.mapper.ScheduleInstanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// 调度实例（ScheduleInstance）仓储实现,基于 MyBatis-Plus。
///
/// 职责:
///
/// - 根据 ID 是否存在决定插入或更新
///   - 不验证调度语义(如触发类型合法性),委托给领域层
///   - 幂等性: 调用方确保不会创建重复的逻辑实例
///
/// 日志策略: DEBUG 级别记录 insert/update 的关键字段;不输出 INFO 日志。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Repository
@RequiredArgsConstructor
public class ScheduleInstanceRepositoryAdapter implements ScheduleInstanceRepository {

  private final ScheduleInstanceMapper mapper;
  private final ScheduleInstanceConverter converter;

  /// 保存或更新调度实例。
  ///
  /// @param instance 调度实例聚合根
  /// @return 持久化后的聚合根(插入时返回转换后的新实例)
  @Override
  public ScheduleInstanceAggregate saveOrUpdateInstance(ScheduleInstanceAggregate instance) {
    Assert.notNull(instance, "ScheduleInstanceAggregate cannot be null");

    ScheduleInstanceDO entity = converter.toDO(instance);
    if (instance.getId() != null) {
      if (log.isDebugEnabled()) {
        log.debug(
            "schedule instance update id={} triggerType={}",
            instance.getId(),
            instance.getTriggerType());
      }
      mapper.updateById(entity);
      return instance;
    }
    mapper.insert(entity);
    if (log.isDebugEnabled()) {
      log.debug(
          "schedule instance insert triggeredAt={} id={} (post-insert)",
          entity.getTriggeredAt(),
          entity.getId());
    }
    return converter.toDomain(entity);
  }
}
