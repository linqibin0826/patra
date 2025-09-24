package com.patra.ingest.infra.persistence.converter;

import com.patra.ingest.domain.model.entity.CursorEvent;
import com.patra.ingest.domain.model.enums.CursorDirection;
import com.patra.ingest.domain.model.enums.CursorType;
import com.patra.ingest.infra.persistence.entity.CursorEventDO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CursorEventConverter {
    CursorEvent toDomain(CursorEventDO source);
    CursorEventDO toDO(CursorEvent source);

    default CursorType mapType(String code){ return code==null? null : CursorType.fromCode(code); }
    default String mapType(CursorType type){ return type==null? null : type.getCode(); }
    default CursorDirection mapDir(String code){ return code==null? null : CursorDirection.fromCode(code); }
    default String mapDir(CursorDirection dir){ return dir==null? null : dir.getCode(); }
}
