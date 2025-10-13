package com.patra.ingest.infra.persistence.repository;

import cn.hutool.core.lang.Assert;
import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;
import com.patra.ingest.domain.port.ScheduleInstanceRepository;
import com.patra.ingest.infra.persistence.converter.ScheduleInstanceConverter;
import com.patra.ingest.infra.persistence.entity.ScheduleInstanceDO;
import com.patra.ingest.infra.persistence.mapper.ScheduleInstanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * Repository implementation for ScheduleInstanceAggregate.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Insert or update based on whether an ID exists.
 *   <li>Do not validate scheduling semantics (e.g., trigger type legality); delegate to the domain.
 *   <li>Idempotency: the caller ensures no duplicate logical instances are created.
 * </ul>
 *
 * <p>Logging: DEBUG key fields on insert/update; no INFO logs here.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ScheduleInstanceRepositoryMpImpl implements ScheduleInstanceRepository {

  private final ScheduleInstanceMapper mapper;
  private final ScheduleInstanceConverter converter;

  /**
   * Save or update a schedule instance.
   *
   * @param instance schedule instance aggregate
   * @return the persisted aggregate (for insert, return the converted new instance)
   */
  @Override
  public ScheduleInstanceAggregate saveOrUpdateInstance(ScheduleInstanceAggregate instance) {
    Assert.notNull(instance, "ScheduleInstanceAggregate cannot be null");

    ScheduleInstanceDO entity = converter.toDO(instance);
    if (instance.getId() != null) {
      if (log.isDebugEnabled()) {
        log.debug(
            "[INGEST][INFRA] schedule instance update id={} triggerType={}",
            instance.getId(),
            instance.getTriggerType());
      }
      mapper.updateById(entity);
      return instance;
    }
    mapper.insert(entity);
    if (log.isDebugEnabled()) {
      log.debug(
          "[INGEST][INFRA] schedule instance insert triggeredAt={} id={} (post-insert)",
          entity.getTriggeredAt(),
          entity.getId());
    }
    return converter.toDomain(entity);
  }
}
