package com.patra.ingest.infra.mapstruct;

import com.patra.ingest.domain.model.entity.CursorEvent;
import com.patra.ingest.infra.persistence.entity.CursorEventDO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * 水位推进事件 · DO ↔ Domain 转换器。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CursorEventConverter {

    CursorEvent toEntity(CursorEventDO src);

    CursorEventDO toDO(CursorEvent src);
}

