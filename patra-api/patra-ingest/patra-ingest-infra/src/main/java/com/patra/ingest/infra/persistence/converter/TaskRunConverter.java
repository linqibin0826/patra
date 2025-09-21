package com.patra.ingest.infra.persistence.converter;

import com.patra.ingest.domain.model.entity.TaskRun;
import com.patra.ingest.infra.persistence.entity.TaskRunDO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TaskRunConverter {
    TaskRun toDomain(TaskRunDO source);
    TaskRunDO toDO(TaskRun source);
}
