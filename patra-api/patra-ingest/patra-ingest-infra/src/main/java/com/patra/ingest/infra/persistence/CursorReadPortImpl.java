package com.patra.ingest.infra.persistence;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.port.CursorReadPort;
import com.patra.ingest.infra.persistence.entity.CursorDO;
import com.patra.ingest.infra.persistence.mapper.CursorMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class CursorReadPortImpl implements CursorReadPort {

    private final CursorMapper cursorMapper;

    @Override
    public Instant loadForwardWatermark(ProvenanceCode provenanceCode, OperationCode operationCode) {
        QueryWrapper<CursorDO> wrapper = new QueryWrapper<>();
        wrapper.eq("provenance_code", provenanceCode.getCode())
                .eq("operation_code", operationCode)
                .eq("cursor_type_code", "TIME")
                .eq("namespace_scope_code", "GLOBAL")
                .orderByDesc("updated_at")
                .last("LIMIT 1");
        CursorDO cursor = cursorMapper.selectOne(wrapper);
        return cursor == null ? null : cursor.getNormalizedInstant();
    }
}
