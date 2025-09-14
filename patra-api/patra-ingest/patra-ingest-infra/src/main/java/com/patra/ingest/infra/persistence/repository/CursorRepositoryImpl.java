package com.patra.ingest.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.aggregate.Cursor;
import com.patra.ingest.domain.model.enums.NamespaceScope;
import com.patra.ingest.domain.model.enums.OperationType;
import com.patra.ingest.domain.port.CursorRepository;
import com.patra.ingest.infra.mapstruct.CursorConverter;
import com.patra.ingest.infra.persistence.entity.CursorDO;
import com.patra.ingest.infra.persistence.mapper.IngCursorMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 通用水位仓储实现。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Repository
@RequiredArgsConstructor
public class CursorRepositoryImpl implements CursorRepository {

    private final IngCursorMapper mapper;
    private final CursorConverter converter;

    @Override
    public Optional<Cursor> findById(Long id) {
        var obj = mapper.selectById(id);
        return Optional.ofNullable(obj).map(converter::toAggregate);
    }

    @Override
    public Optional<Cursor> findByUniqueKey(ProvenanceCode provenanceCode, OperationType operation, String cursorKey, NamespaceScope namespaceScope, String namespaceKey) {
        var q = new LambdaQueryWrapper<CursorDO>()
                .eq(CursorDO::getLiteratureProvenanceCode, provenanceCode)
                .eq(CursorDO::getOperation, operation)
                .eq(CursorDO::getCursorKey, cursorKey)
                .eq(CursorDO::getNamespaceScope, namespaceScope)
                .eq(CursorDO::getNamespaceKey, namespaceKey)
                .last("limit 1");
        var obj = mapper.selectOne(q);
        return Optional.ofNullable(obj).map(converter::toAggregate);
    }

    @Override
    public Cursor save(Cursor cursor) {
        var toSave = converter.toDO(cursor);
        if (toSave.getId() == null) mapper.insert(toSave); else mapper.updateById(toSave);
        return converter.toAggregate(toSave);
    }
}

