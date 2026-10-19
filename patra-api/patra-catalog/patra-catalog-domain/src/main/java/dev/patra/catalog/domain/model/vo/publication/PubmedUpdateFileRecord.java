package dev.patra.catalog.domain.model.vo.publication;

import dev.patra.catalog.domain.model.enums.PubmedUpdateFileStatus;
import dev.patra.catalog.domain.model.enums.PubmedUpdateScope;
import java.time.Instant;
import lombok.Builder;

/// 增量同步进度表中一个文件的记录。
///
/// @param fileName 文件名，如 `pubmed26n1335.xml.gz`
/// @param fileSeq 文件号
/// @param status 状态
/// @param scope 本次处理的导入范围
/// @param counts 处理计数
/// @param startedAt 开始时间
/// @param finishedAt 结束时间，未结束为空
/// @param lastError 失败原因，未失败为空
/// @author linqibin
/// @since 0.11.0
@Builder
public record PubmedUpdateFileRecord(
    String fileName,
    int fileSeq,
    PubmedUpdateFileStatus status,
    PubmedUpdateScope scope,
    PubmedUpdateFileCounts counts,
    Instant startedAt,
    Instant finishedAt,
    String lastError) {}
