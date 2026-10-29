package dev.patra.catalog.domain.fulltext.model.read;

import dev.patra.catalog.domain.kernel.model.enums.FullTextStatus;
import java.time.LocalDate;
import lombok.Builder;

/// 一个待抓取全文的文献。
///
/// @param publicationId 文献 ID
/// @param pmcid 当前的 pmc 标识符（`cat_publication_identifier.type = 'pmc'`）
/// @param currentStatus 已有全文记录的状态，没有记录时为 `null`
/// @param attemptCount 已有记录里同一状态的连续次数，没有记录时为 0
/// @param pmcReleaseDate PMC 放开日期，没有时为 `null`
/// @author linqibin
/// @since 0.11.0
@Builder
public record FetchCandidate(
    long publicationId,
    String pmcid,
    FullTextStatus currentStatus,
    int attemptCount,
    LocalDate pmcReleaseDate) {}
