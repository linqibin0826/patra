package com.patra.ingest.infra.persistence.repository;

import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;
import com.patra.ingest.domain.port.ScheduleInstanceRepository;
import com.patra.ingest.infra.persistence.converter.ScheduleInstanceConverter;
import com.patra.ingest.infra.persistence.entity.ScheduleInstanceDO;
import com.patra.ingest.infra.persistence.mapper.ScheduleInstanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ScheduleInstanceRepositoryMpImpl implements ScheduleInstanceRepository {

    private final ScheduleInstanceMapper mapper;
    private final ScheduleInstanceConverter converter;

    @Override
    public ScheduleInstanceAggregate save(ScheduleInstanceAggregate instance) {
        ScheduleInstanceDO entity = converter.toDO(instance);
        if (entity.getId() == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
        return converter.toDomain(entity);
    }
}
