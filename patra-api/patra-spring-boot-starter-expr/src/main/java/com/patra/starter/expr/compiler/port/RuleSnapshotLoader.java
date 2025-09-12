package com.patra.starter.expr.compiler.port;

import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;

import java.util.Optional;

/**
 * 规则快照加载端口（Hex outbound Port）。
 * 由具体渠道（Feign/File/Inline…）实现。
 */
public interface RuleSnapshotLoader {

    /**
     * 按业务键加载（推荐使用 code，如 pubmed）。
     * @param provenanceCode 平台数据源业务键
     * @param operation      操作名：search/fetch/lookup…
     */
    Optional<SnapshotWithVersion> loadByCode(String provenanceCode, String operation);

    /**
     * 按技术键加载（可选）。
     */
    default Optional<SnapshotWithVersion> loadById(Long provenanceId, String operation) {
        return Optional.empty();
    }

    /**
     * 封装版本信息，便于缓存与灰度。
     */
    record SnapshotWithVersion(
            ProvenanceSnapshot snapshot,
            String versionTag,     // 例如 registry 的 version/updatedAt 哈希；文件源可用 lastModified 哈希
            long loadedEpochMillis // 装载时间戳，用于 TTL 判断
    ) {}
}
