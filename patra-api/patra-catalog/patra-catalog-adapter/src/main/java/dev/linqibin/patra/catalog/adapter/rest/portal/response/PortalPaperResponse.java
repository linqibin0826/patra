package dev.linqibin.patra.catalog.adapter.rest.portal.response;

import java.util.List;

/// Portal 文献流列表项响应。
///
/// 字段名与类型直接对齐前端 `Paper`，前端零字段映射。
///
/// @param id 文献主键（String，避免 JS 超 2^53 精度损失）
/// @param title 标题
/// @param journal 载体名称（可空）
/// @param year 出版年份（可空）
/// @param authors 全部作者展示名
/// @param cites 被引次数（可空）
/// @param bookmarks 收藏数（无用户系统，恒为 0）
/// @param doi DOI（可空）
/// @param pmid PubMed ID（可空）
/// @param source 数据来源展示名
/// @param aiSummary AI 速读（本版恒 null，TODO 接入 LLM 摘要）
/// @param estimatedReadMin 原文预计阅读时长（本版恒 null，TODO 接入原文采集/字数估算）
/// @param kind 文献类型（可空）
/// @param minutesAgo 距最后采集的分钟数（可空）
/// @param venueId 载体主键（String，可空）
/// @param evidenceLevel 证据等级视图（与详情端点同形）
/// @param abstractSnippet 摘要可见纯文本前 300 码点（可空）
/// @author linqibin
/// @since 0.1.0
public record PortalPaperResponse(
    String id,
    String title,
    String journal,
    Integer year,
    List<String> authors,
    Integer cites,
    Integer bookmarks,
    String doi,
    String pmid,
    String source,
    String aiSummary,
    Integer estimatedReadMin,
    String kind,
    Integer minutesAgo,
    String venueId,
    EvidenceLevelView evidenceLevel,
    String abstractSnippet) {

  public PortalPaperResponse {
    authors = authors != null ? List.copyOf(authors) : List.of();
  }
}
