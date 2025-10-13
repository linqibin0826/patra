package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.CursorEvent;

/**
 * Repository port for cursor advancement events.
 *
 * <p>Persists append-only records to capture cursor lineage, enabling audit trails, state replay,
 * and monitoring.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface CursorEventRepository {

  /**
   * Persist a cursor advancement event.
   *
   * @param event cursor event containing identifiers, window, lineage, and metadata
   * @return saved event, typically with a generated identifier
   */
  CursorEvent save(CursorEvent event);
}
