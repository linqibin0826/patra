package com.patra.starter.expr.compiler.port;

import java.util.Optional;

/**
 * 规则快照缓存端口（可内存/分布式实现）。
 */
public interface RuleSnapshotCache {

    Optional<RuleSnapshotLoader.SnapshotWithVersion> get(String provenanceCode, String operation);

    void put(String provenanceCode, String operation, RuleSnapshotLoader.SnapshotWithVersion val);

    void evict(String provenanceCode, String operation);
}
