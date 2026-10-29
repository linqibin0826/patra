package dev.patra.catalog.adapter.fulltext.rest.response;

import dev.patra.catalog.domain.fulltext.model.vo.body.FullTextBody;
import java.time.Instant;
import lombok.Builder;

/// 门户全文接口 `GET /portal/publications/{id}/fulltext` 的响应（工程 spec 第 7.3 节）。
///
/// @param publicationId 文献 ID（字符串）
/// @param pmcid PMCID
/// @param pmcUrl PMC 文章页链接
/// @param license 许可，不认识时为 null（门户显示"许可见原文"）
/// @param source 数据来源署名（固定文案）
/// @param fetchedAt 抓取时间（页面显示的抓取日期）
/// @param wordCount 字数，只有 PDF 时为 null
/// @param estimatedReadMin 阅读分钟数，只有 PDF 时为 null
/// @param isManuscript 是否作者手稿
/// @param body 正文，只有 PDF 时为 null
/// @author linqibin
/// @since 0.11.0
@Builder
public record PortalFullTextResponse(
    String publicationId,
    String pmcid,
    String pmcUrl,
    License license,
    String source,
    Instant fetchedAt,
    Integer wordCount,
    Integer estimatedReadMin,
    Boolean isManuscript,
    FullTextBody body) {

  /// 许可。
  ///
  /// @param code 许可原值
  /// @param url 官方链接
  public record License(String code, String url) {

    /// 创建许可。
    ///
    /// @param code 许可原值
    /// @param url 官方链接
    /// @return 许可
    public static License of(String code, String url) {
      return new License(code, url);
    }
  }
}
