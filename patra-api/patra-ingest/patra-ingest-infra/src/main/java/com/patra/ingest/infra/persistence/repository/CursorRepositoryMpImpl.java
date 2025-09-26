package com.patra.ingest.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.patra.ingest.domain.model.entity.Cursor;
import com.patra.ingest.domain.port.CursorRepository;
import com.patra.ingest.infra.persistence.converter.CursorConverter;
import com.patra.ingest.infra.persistence.entity.CursorDO;
import com.patra.ingest.infra.persistence.mapper.CursorMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.time.Instant;

@Repository
@RequiredArgsConstructor
public class CursorRepositoryMpImpl implements CursorRepository {

    private final CursorMapper mapper;
    private final CursorConverter converter;

    @Override
    public Cursor save(Cursor cursor) {
        CursorDO dto = converter.toDO(cursor);
        if (dto.getId() == null) mapper.insert(dto); else mapper.updateById(dto);
        CursorDO persisted = mapper.selectById(dto.getId());
        return converter.toDomain(persisted);
    }

    @Override
    public Optional<Cursor> find(String provenanceCode, String operationCode, String cursorKey, String namespaceScopeCode, String namespaceKey) {
        CursorDO found = mapper.selectOne(new QueryWrapper<CursorDO>()
            .eq("provenance_code", provenanceCode)
            .eq("operation_code", operationCode)
            .eq("cursor_key", cursorKey)
            .eq("namespace_scope_code", namespaceScopeCode)
            .eq("namespace_key", namespaceKey));
        return Optional.ofNullable(found).map(converter::toDomain);
    }

    @Override
    public Optional<Instant> findLatestGlobalTimeWatermark(String provenanceCode, String operationCode) {
        QueryWrapper<CursorDO> wrapper = new QueryWrapper<>();
        wrapper.eq("provenance_code", provenanceCode);
        if (operationCode != null) {
            wrapper.eq("operation_code", operationCode);
        }
        wrapper.eq("cursor_type_code", "TIME")
               .eq("namespace_scope_code", "GLOBAL")
               .orderByDesc("updated_at")
               .last("LIMIT 1");
        CursorDO one = mapper.selectOne(wrapper);
        return Optional.ofNullable(one).map(CursorDO::getNormalizedInstant);
    }
}
