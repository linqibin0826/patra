package dev.patra.catalog.domain.publication.model.vo;

import dev.patra.catalog.domain.publication.model.enums.PubmedFileKind;
import dev.patra.catalog.domain.publication.model.enums.PubmedFileStatus;
import dev.patra.catalog.domain.publication.model.enums.PubmedImportScope;
import java.time.Instant;
import lombok.Builder;

/// 增量同步进度表中一个文件的记录。
///
/// @param fileName 文件名，如 `pubmed26n1335.xml.gz`
/// @param kind 文件种类
/// @param fileSeq 文件号
/// @param status 状态
/// @param scope 本次处理的导入范围
/// @param counts 处理计数
/// @param startedAt 开始时间
/// @param finishedAt 结束时间，未结束为空
/// @param lastError 失败原因，未失败为空
/// @param generation 这次处理所用的作业 generation，首次处理为空；失败后重试沿用它
/// @author linqibin
/// @since 0.11.0
@Builder
public record PubmedFileRecord(
    String fileName,
    int fileSeq,
    PubmedFileKind kind,
    PubmedFileStatus status,
    PubmedImportScope scope,
    PubmedFileCounts counts,
    Instant startedAt,
    Instant finishedAt,
    String lastError,
    String generation) {}
