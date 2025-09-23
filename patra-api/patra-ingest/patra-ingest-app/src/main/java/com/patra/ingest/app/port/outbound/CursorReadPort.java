package com.patra.ingest.app.port.outbound;

import java.time.Instant;

public interface CursorReadPort {

    Instant loadForwardWatermark(String provenanceCode, String operationCode);
}
