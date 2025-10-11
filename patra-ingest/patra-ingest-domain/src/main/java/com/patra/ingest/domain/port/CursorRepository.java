package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.Cursor;

import java.time.Instant;
import java.util.Optional;

/**
 * Repository port for ingestion cursors.
 * <p>Persists and retrieves cursor watermarks so ingestion/cleansing flows avoid duplication or window drift.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface CursorRepository {

    /**
     * Retrieve the cursor by its business key.
     *
     * @param provenanceCode provenance code
     * @param operationCode  operation code (nullable filter)
     * @param cursorKey      cursor identifier (for example data domain or resource type)
     * @param namespaceScope namespace scope code
     * @param namespaceKey   namespace business key
     * @return matching cursor or {@link Optional#empty()}
     */
    Optional<Cursor> find(String provenanceCode,
                          String operationCode,
                          String cursorKey,
                          String namespaceScope,
                          String namespaceKey);

    /**
     * Persist or update the cursor state.
     *
     * @param cursor cursor entity with current watermark, namespace, and version info
     * @return persisted cursor
     */
    Cursor save(Cursor cursor);

    /**
     * Fetch the latest normalized time watermark for the GLOBAL namespace/time cursor.
     *
     * @param provenanceCode provenance code
     * @param operationCode  operation code filter (nullable)
     * @return most recent GLOBAL time watermark or {@link Optional#empty()}
     */
    Optional<Instant> findLatestGlobalTimeWatermark(String provenanceCode, String operationCode);
}
