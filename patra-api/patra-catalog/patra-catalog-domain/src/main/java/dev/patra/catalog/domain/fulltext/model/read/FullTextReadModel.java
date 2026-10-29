package dev.patra.catalog.domain.fulltext.model.read;

import dev.patra.catalog.domain.fulltext.model.vo.body.FullTextBody;
import java.time.Instant;
import lombok.Builder;

/// 门户全文接口的读模型。
///
/// 可读全文时带正文与字数；不可读但 PDF 可下载（典型是 `NO_BODY`）时正文与字数为空，署名字段照常——
/// 页面在分发 PDF，同样要署名（release spec 决策 D）。
///
/// @param publicationId 文献 ID
/// @param pmcid PMCID
/// @param licenseCode 许可原值，可空
/// @param fetchedAt 抓取时间
/// @param wordCount 字数，不可读时为空
/// @param manuscript 是否作者手稿
/// @param body 正文，不可读时为空
/// @author linqibin
/// @since 0.11.0
@Builder
public record FullTextReadModel(
    long publicationId,
    String pmcid,
    String licenseCode,
    Instant fetchedAt,
    Integer wordCount,
    boolean manuscript,
    FullTextBody body) {}
