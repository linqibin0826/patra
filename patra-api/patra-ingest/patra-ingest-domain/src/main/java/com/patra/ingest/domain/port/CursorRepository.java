package com.patra.ingest.domain.port;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.aggregate.Cursor;
import com.patra.ingest.domain.model.enums.IngestOperationType;
import com.patra.ingest.domain.model.enums.NamespaceScope;

import java.util.Optional;

/**
 * 通用水位仓储端口。
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface CursorRepository {

    Optional<Cursor> findById(Long id);

    Optional<Cursor> findByUniqueKey(ProvenanceCode provenanceCode,
                                     IngestOperationType operation,
                                     String cursorKey,
                                     NamespaceScope namespaceScope,
                                     String namespaceKey);

    Cursor save(Cursor cursor);
}

