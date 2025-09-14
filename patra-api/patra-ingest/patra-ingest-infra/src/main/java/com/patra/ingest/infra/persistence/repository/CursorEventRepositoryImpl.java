package com.patra.ingest.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.entity.CursorEvent;
import com.patra.ingest.domain.model.enums.NamespaceScope;
import com.patra.ingest.domain.model.enums.OperationType;
import com.patra.ingest.domain.port.CursorEventRepository;
import com.patra.ingest.infra.mapstruct.CursorEventConverter;
import com.patra.ingest.infra.persistence.entity.CursorEventDO;
import com.patra.ingest.infra.persistence.mapper.IngCursorEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 水位推进事件仓储实现。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Repository
@RequiredArgsConstructor
public class CursorEventRepositoryImpl implements CursorEventRepository {

    private final IngCursorEventMapper mapper;
    private final CursorEventConverter converter;

    @Override
    public Optional<CursorEvent> findById(Long id) {
        var obj = mapper.selectById(id);
        return Optional.ofNullable(obj).map(converter::toEntity);
    }

    @Override
    public Optional<CursorEvent> findByIdempotentKey(String idempotentKey) {
        var q = new LambdaQueryWrapper<CursorEventDO>()
                .eq(CursorEventDO::getIdempotentKey, idempotentKey)
                .last("limit 1");
        var obj = mapper.selectOne(q);
        return Optional.ofNullable(obj).map(converter::toEntity);
    }

    @Override
    public List<CursorEvent> findRecentByTimeline(ProvenanceCode provenanceCode, OperationType operation, String cursorKey, NamespaceScope namespaceScope, String namespaceKey, int limit) {
        int lim = Math.max(1, Math.min(limit, 1000));
        var q = new LambdaQueryWrapper<CursorEventDO>()
                .eq(CursorEventDO::getLiteratureProvenanceCode, provenanceCode)
                .eq(CursorEventDO::getOperation, operation)
                .eq(CursorEventDO::getCursorKey, cursorKey)
                .eq(CursorEventDO::getNamespaceScope, namespaceScope)
                .eq(CursorEventDO::getNamespaceKey, namespaceKey)
                .orderByDesc(CursorEventDO::getId)
                .last("limit " + lim);
        var list = mapper.selectList(q);
        return list.stream().map(converter::toEntity).toList();
    }

    @Override
    public CursorEvent save(CursorEvent event) {
        var toSave = converter.toDO(event);
        if (toSave.getId() == null) mapper.insert(toSave); else mapper.updateById(toSave);
        return converter.toEntity(toSave);
    }
}
