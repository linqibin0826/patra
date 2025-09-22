package com.patra.ingest.infra.persistence.converter;

import com.patra.ingest.domain.model.aggregate.Task;
import com.patra.ingest.domain.model.vo.IdempotentKey;
import com.patra.ingest.domain.model.vo.TaskParams;
import com.patra.ingest.infra.persistence.entity.TaskDO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TaskConverter {
    Task toDomain(TaskDO source);
    TaskDO toDO(Task source);

    default IdempotentKey mapIdempotentKey(String raw){ return raw==null? null : new IdempotentKey(raw); }
    default String mapIdempotentKey(IdempotentKey key){ return key==null? null : key.value(); }
    default TaskParams mapParams(String raw){ return raw==null? null : new TaskParams(null); }
    default String mapParams(TaskParams params){ return params==null? null : "{}"; }
}
