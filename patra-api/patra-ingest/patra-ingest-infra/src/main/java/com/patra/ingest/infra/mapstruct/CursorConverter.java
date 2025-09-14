package com.patra.ingest.infra.mapstruct;

import com.patra.ingest.domain.model.aggregate.Cursor;
import com.patra.ingest.infra.persistence.entity.CursorDO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * Cursor 聚合 ↔ CursorDO 转换器。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CursorConverter {

    Cursor toAggregate(CursorDO src);

    CursorDO toDO(Cursor src);
}
