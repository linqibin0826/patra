package com.patra.ingest.infra.persistence.converter;

import com.patra.ingest.domain.model.aggregate.Plan;
import com.patra.ingest.domain.model.enums.PlanStatus;
import com.patra.ingest.infra.persistence.entity.PlanDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PlanConverter {
    
    @Mapping(source = "statusCode", target = "status")
    @Mapping(source = "sliceParams", target = "sliceParamsJson")
    Plan toDomain(PlanDO planDO);
    
    @Mapping(source = "status", target = "statusCode")  
    @Mapping(source = "sliceParamsJson", target = "sliceParams")
    PlanDO toDO(Plan plan);

    default PlanStatus mapStatus(String code) { 
        return code == null ? null : PlanStatus.valueOf(code); 
    }
    
    default String mapStatus(PlanStatus status) { 
        return status == null ? null : status.name(); 
    }
}
