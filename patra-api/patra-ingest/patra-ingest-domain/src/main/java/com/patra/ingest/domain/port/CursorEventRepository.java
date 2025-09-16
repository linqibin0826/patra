package com.patra.ingest.domain.port;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.entity.CursorEvent;
import com.patra.ingest.domain.model.enums.NamespaceScope;
import com.patra.ingest.domain.model.enums.IngestOperationType;

import java.util.List;
import java.util.Optional;

/**
 * 水位推进事件仓储端口。
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface CursorEventRepository {

    /** 按ID查询 */
    Optional<CursorEvent> findById(Long id);

    /** 按幂等键查询 */
    Optional<CursorEvent> findByIdempotentKey(String idempotentKey);

    /** 按时间线维度查询最近的事件列表 */
    List<CursorEvent> findRecentByTimeline(ProvenanceCode provenanceCode,
                                           IngestOperationType operation,
                                           String cursorKey,
                                           NamespaceScope namespaceScope,
                                           String namespaceKey,
                                           int limit);

    /** 保存事件 */
    CursorEvent save(CursorEvent event);
}
