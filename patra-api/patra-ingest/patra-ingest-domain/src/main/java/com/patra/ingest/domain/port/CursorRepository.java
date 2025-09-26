package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.Cursor;

import java.time.Instant;
import java.util.Optional;

public interface CursorRepository {
    Optional<Cursor> find(String provenanceCode, String operationCode, String cursorKey, String namespaceScope, String namespaceKey);
    Cursor save(Cursor cursor);

    /**
     * 查询 GLOBAL 命名空间、TIME 类型游标的最新标准化时间水位。
     * @param provenanceCode 来源编码
     * @param operationCode 操作编码（可空时忽略条件）
     * @return 最新水位时间（不存在则 empty）
     */
    Optional<Instant> findLatestGlobalTimeWatermark(String provenanceCode, String operationCode);
}
