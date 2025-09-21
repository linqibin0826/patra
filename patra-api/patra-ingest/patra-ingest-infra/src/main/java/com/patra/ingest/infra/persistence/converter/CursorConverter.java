package com.patra.ingest.infra.persistence.converter;

import com.patra.ingest.domain.model.entity.Cursor;
import com.patra.ingest.domain.model.enums.CursorType;
import com.patra.ingest.infra.persistence.entity.CursorDO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CursorConverter {
    Cursor toDomain(CursorDO source);
    CursorDO toDO(Cursor source);

    default CursorType mapType(String code){ return code==null? null : CursorType.valueOf(code); }
    default String mapType(CursorType type){ return type==null? null : type.name(); }
}
