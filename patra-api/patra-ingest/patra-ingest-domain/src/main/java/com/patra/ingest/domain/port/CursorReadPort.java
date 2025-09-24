package com.patra.ingest.domain.port;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.OperationCode;

import java.time.Instant;

/**
 * 读取游标（前向水位）端口（Domain Port）
 */
public interface CursorReadPort {

    // TODO 水位查询待优化（namespaceKey, cursorKey）两个维度的
    Instant loadForwardWatermark(ProvenanceCode provenanceCode, OperationCode operationCode);
}
