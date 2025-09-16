// src/main/java/com/example/ingest/app/command/IngestScope.java
package com.patra.ingest.app.usecase.command;

import java.util.List;
import java.util.Optional;

/**
 * 业务语义的采集范围示例：可按期刊、机构、学科等限定。
 * 这里给一个轻量示例；真实项目可扩展更多维度。
 */
public record IngestScope(
        Optional<List<String>> journalIssns,
        Optional<List<String>> affiliations,
        Optional<List<String>> subjectAreas
) {
    public IngestScope {
        journalIssns = journalIssns == null ? Optional.empty() : journalIssns;
        affiliations = affiliations == null ? Optional.empty() : affiliations;
        subjectAreas = subjectAreas == null ? Optional.empty() : subjectAreas;
    }

    public static IngestScope empty() {
        return new IngestScope(Optional.empty(), Optional.empty(), Optional.empty());
    }
}
