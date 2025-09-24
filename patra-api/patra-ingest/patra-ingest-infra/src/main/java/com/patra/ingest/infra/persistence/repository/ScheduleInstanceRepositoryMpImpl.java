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

@Slf4j
@Repository
@RequiredArgsConstructor
public class ScheduleInstanceRepositoryMpImpl implements ScheduleInstanceRepository {

    private final ScheduleInstanceMapper mapper;
    private final ScheduleInstanceConverter converter;

    @Override
    public ScheduleInstanceAggregate saveOrUpdateInstance(ScheduleInstanceAggregate instance) {
        Assert.notNull(instance, "ScheduleInstanceAggregate cannot be null");

        ScheduleInstanceDO entity = converter.toDO(instance);
        if (instance.getId() != null) {
            log.info("Updating ScheduleInstance with ID: {}", instance.getId());
            mapper.updateById(entity);
            return instance;
        }
        mapper.insert(entity);
        return converter.toDomain(entity);
    }
}
