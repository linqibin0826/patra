package com.patra.ingest.infra.persistence.repository;

import com.patra.ingest.domain.model.aggregate.ScheduleInstance;
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
    public ScheduleInstance save(ScheduleInstance instance) {
        ScheduleInstanceDO dto = converter.toDO(instance);
        if (dto.getId() == null) mapper.insert(dto); else mapper.updateById(dto);
        return converter.toDomain(dto);
    }
}
