package dev.linqibin.patra.catalog.domain.model.read.portal;

import dev.linqibin.patra.catalog.domain.model.vo.publication.EvidenceLevel;
import java.time.Instant;
import java.util.List;
import lombok.Builder;

/// Portal 文献列表项读模型（CQRS 读端），首页 feed 与文献检索共用。
///
/// 字段来源于 `cat_publication` 主表 + venue / author / publication_type / abstract 关联，
/// 由 Infra 层一次查询组装。`aiSummary` / `estimatedReadMin` 不在读模型中——
/// 后端暂无数据来源，由 Adapter 层 Response 置 null。
///
/// @param id 文献主键
/// @param title 标题（含内联标记原文）
/// @param venueId 载体主键（可空）
/// @param venueName 载体名称（可空）
/// @param publicationYear 出版年份（可空）
/// @param authors 全部作者展示名，按 author_order 升序（无作者则空列表）
/// @param citationCount 被引次数（可空）
/// @param doi DOI（可空）
/// @param pmid PubMed ID（可空）
/// @param provenanceCode 数据来源代码
/// @param studyType 文献类型（第一个 publication_type，可空）
/// @param evidenceLevel 证据等级（由全部 publication_type 衍生；null 时替换为 UNKNOWN）
/// @param abstractSnippet 摘要可见纯文本前 300 码点（可空）
/// @param lastSyncedAt 最后采集时间（可空）
/// @author linqibin
/// @since 0.1.0
@Builder
public record PortalPaperReadModel(
    Long id,
    String title,
    Long venueId,
    String venueName,
    Integer publicationYear,
    List<String> authors,
    Integer citationCount,
    String doi,
    String pmid,
    String provenanceCode,
    String studyType,
    EvidenceLevel evidenceLevel,
    String abstractSnippet,
    Instant lastSyncedAt) {

  /// 紧凑构造器：authors 防御性拷贝；evidenceLevel 为 null 时降级为 [EvidenceLevel.UNKNOWN]。
  public PortalPaperReadModel {
    authors = authors != null ? List.copyOf(authors) : List.of();
    if (evidenceLevel == null) {
      evidenceLevel = EvidenceLevel.UNKNOWN;
    }
  }
}
