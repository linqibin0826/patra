package com.patra.ingest.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.patra.ingest.domain.model.entity.CursorEvent;
import com.patra.ingest.domain.port.CursorEventRepository;
import com.patra.ingest.infra.persistence.converter.CursorEventConverter;
import com.patra.ingest.infra.persistence.entity.CursorEventDO;
import com.patra.ingest.infra.persistence.mapper.CursorEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class CursorEventRepositoryMpImpl implements CursorEventRepository {

    private final CursorEventMapper mapper;
    private final CursorEventConverter converter;

    @Override
    public CursorEvent save(CursorEvent event) {
        CursorEventDO dto = converter.toDO(event);
        if (dto.getId() == null) mapper.insert(dto); else mapper.updateById(dto);
        CursorEventDO persisted = mapper.selectById(dto.getId());
        return converter.toDomain(persisted);
    }
}
