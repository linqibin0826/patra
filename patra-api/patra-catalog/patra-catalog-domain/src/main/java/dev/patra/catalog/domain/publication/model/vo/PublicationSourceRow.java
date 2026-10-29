package dev.patra.catalog.domain.publication.model.vo;

import java.time.Instant;
import lombok.Builder;

/// 来源表 `cat_publication_source` 的一行：一篇文献最后一次写入（或软删）它的文件与该文件里的原文。
///
/// @param pmid PMID
/// @param sourceFile 文件名
/// @param sourceRank 来源位置（见 [SourcePosition]）
/// @param rawXml 原文；墓碑或只记软删位置时为 null
/// @param parsedAt 最近一次按原文解析写入的时间，可空
/// @author linqibin
/// @since 0.1.0
@Builder
public record PublicationSourceRow(
    String pmid, String sourceFile, int sourceRank, String rawXml, Instant parsedAt) {}
