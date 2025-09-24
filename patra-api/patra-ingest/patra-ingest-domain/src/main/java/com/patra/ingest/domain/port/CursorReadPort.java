package com.patra.ingest.domain.port;

import java.time.Instant;

/**
 * 读取游标（前向水位）端口（Domain Port）
 */
public interface CursorReadPort {

    Instant loadForwardWatermark(String provenanceCode, String operationCode);
}
