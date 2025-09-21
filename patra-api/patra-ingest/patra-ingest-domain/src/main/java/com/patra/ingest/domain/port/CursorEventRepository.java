package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.CursorEvent;
public interface CursorEventRepository {
    CursorEvent save(CursorEvent event);
}
