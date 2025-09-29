package com.patra.ingest.infra.persistence.repository;

import com.patra.ingest.domain.model.entity.CursorEvent;
import com.patra.ingest.domain.port.CursorEventRepository;
import com.patra.ingest.infra.persistence.converter.CursorEventConverter;
import com.patra.ingest.infra.persistence.entity.CursorEventDO;
import com.patra.ingest.infra.persistence.mapper.CursorEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;


/**
 * 游标推进事件（CursorEvent）仓储实现。
 * <p>职责：记录游标 prev → new 的推进轨迹（含窗口与水位），支撑审计 / 回放。</p>
 * <p>仅提供保存；不提供复杂检索（需统计请建立独立 Query 组件）。</p>
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class CursorEventRepositoryMpImpl implements CursorEventRepository {

    private final CursorEventMapper mapper;
    private final CursorEventConverter converter;

    /**
     * 保存游标事件。
     * @param event 游标事件聚合
     * @return 持久化后聚合
     */
    @Override
    public CursorEvent save(CursorEvent event) {
        CursorEventDO dto = converter.toDO(event);
        if (dto.getId() == null) {
            mapper.insert(dto);
            if (log.isDebugEnabled()) {
                log.debug("[INGEST][INFRA] cursor event insert cursorKey={} wm={}", dto.getCursorKey(), dto.getNewInstant());
            }
        } else {
            mapper.updateById(dto);
            if (log.isDebugEnabled()) {
                log.debug("[INGEST][INFRA] cursor event update id={} cursorKey={} wm={}", dto.getId(), dto.getCursorKey(), dto.getNewInstant());
            }
        }
        CursorEventDO persisted = mapper.selectById(dto.getId());
        return converter.toDomain(persisted);
    }
}
