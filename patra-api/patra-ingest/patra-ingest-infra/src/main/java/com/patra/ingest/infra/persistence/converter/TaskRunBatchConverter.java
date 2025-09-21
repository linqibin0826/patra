package com.patra.ingest.infra.persistence.converter;

import com.patra.ingest.domain.model.entity.TaskRunBatch;
import com.patra.ingest.domain.model.vo.IdempotentKey;
import com.patra.ingest.infra.persistence.entity.TaskRunBatchDO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TaskRunBatchConverter {
    TaskRunBatch toDomain(TaskRunBatchDO source);
    TaskRunBatchDO toDO(TaskRunBatch source);

    default IdempotentKey mapIdempotentKey(String raw){ return raw==null? null : new IdempotentKey(raw); }
    default String mapIdempotentKey(IdempotentKey key){ return key==null? null : key.value(); }
}
