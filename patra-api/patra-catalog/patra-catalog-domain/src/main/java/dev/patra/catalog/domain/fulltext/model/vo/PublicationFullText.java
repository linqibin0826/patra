package dev.patra.catalog.domain.fulltext.model.vo;

import dev.patra.catalog.domain.fulltext.model.enums.FullTextUnavailableReason;
import dev.patra.catalog.domain.kernel.model.enums.FullTextStatus;
import java.time.Instant;
import java.util.List;
import lombok.Builder;

/// 一篇文献的全文记录（`cat_publication_fulltext` 一行）。
///
/// @param publicationId 文献 ID
/// @param pmcid 抓取时用的 PMCID
/// @param pmcVersion 选中的版本前缀，如 `PMC1234567.2`
/// @param status 状态
/// @param unavailableReason 不可用原因，仅 `UNAVAILABLE` 时有值
/// @param licenseCode PMC 元数据里的许可原值，可空
/// @param manuscript 是否作者手稿
/// @param objectPrefix MinIO 对象前缀，到版本目录为止
/// @param xmlMd5 XML 的 md5
/// @param pdfObjectKey PDF 对象键，无 PDF 为空
/// @param pdfSize PDF 字节数
/// @param pdfMd5 PDF 的 md5，作为下载的 ETag
/// @param media 已下载的正文图片
/// @param fetchedAt 抓取时间（页面显示的抓取日期）
/// @param parserVersion 解析成功或失败时的解析器版本
/// @param parsedAt 解析时间
/// @param wordCount 正文字数
/// @param attemptCount 同一状态连续出现的次数，成功后清零
/// @param lastError 最近一次失败原因
/// @param nextAttemptAt 下次重试或再查时间
/// @author linqibin
/// @since 0.11.0
@Builder(toBuilder = true)
public record PublicationFullText(
    Long publicationId,
    String pmcid,
    String pmcVersion,
    FullTextStatus status,
    FullTextUnavailableReason unavailableReason,
    String licenseCode,
    Boolean manuscript,
    String objectPrefix,
    String xmlMd5,
    String pdfObjectKey,
    Long pdfSize,
    String pdfMd5,
    List<FullTextMedia> media,
    Instant fetchedAt,
    Integer parserVersion,
    Instant parsedAt,
    Integer wordCount,
    int attemptCount,
    String lastError,
    Instant nextAttemptAt) {

  /// 防御性拷贝 `media`，`null` 视为空。
  public PublicationFullText {
    media = media != null ? List.copyOf(media) : List.of();
  }
}
