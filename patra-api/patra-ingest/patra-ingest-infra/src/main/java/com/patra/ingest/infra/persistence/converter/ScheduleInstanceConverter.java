package com.patra.ingest.infra.persistence.converter;

import com.patra.ingest.domain.model.aggregate.ScheduleInstance;
import com.patra.ingest.infra.persistence.entity.ScheduleInstanceDO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ScheduleInstanceConverter {
    ScheduleInstance toDomain(ScheduleInstanceDO source);
    ScheduleInstanceDO toDO(ScheduleInstance source);
}
