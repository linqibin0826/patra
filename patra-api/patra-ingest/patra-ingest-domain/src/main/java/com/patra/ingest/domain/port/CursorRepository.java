package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.Cursor;
import java.util.Optional;

public interface CursorRepository {
    Optional<Cursor> find(String provenanceCode,String operationCode,String cursorKey,String namespaceScope,String namespaceKey);
    Cursor save(Cursor cursor);
}
