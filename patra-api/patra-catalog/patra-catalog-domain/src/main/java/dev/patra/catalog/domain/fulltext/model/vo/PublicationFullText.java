package dev.patra.catalog.domain.fulltext.model.vo;

import dev.patra.catalog.domain.fulltext.event.PmcFullTextFetchedEvent;
import dev.patra.catalog.domain.fulltext.model.enums.FullTextUnavailableReason;
import dev.patra.catalog.domain.fulltext.model.read.ParseCandidate;
import dev.patra.catalog.domain.kernel.model.enums.FullTextStatus;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
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

  /// 「PMC 全文已抓取」事件。只对抓取成功的记录有意义：其他状态没有 `manuscript`，这里会抛空指针。
  ///
  /// @return 事件
  public PmcFullTextFetchedEvent fetchedEvent() {
    return new PmcFullTextFetchedEvent(publicationId, pmcid, licenseCode, manuscript);
  }

  /// 是否仍是这条解析候选：状态还在等解析，而且还是候选那份 XML（同一对象前缀与版本）。
  ///
  /// 解析在事务外读对象存储，期间抓取任务可能已重抓、换了版本或状态。持久化事务锁住行后用它判断
  /// 解析结果还能不能写；不成立时丢弃结果，新内容留给下一轮。状态条件与解析候选查询一致：
  /// 待解析，或解析成功 / 失败 / 无正文但解析器版本落后。
  ///
  /// @param candidate 解析时用的候选
  /// @param currentParserVersion 当前解析器版本
  /// @return 解析结果仍可写入时为 true
  public boolean stillAwaitsParsing(ParseCandidate candidate, int currentParserVersion) {
    boolean sameXml =
        Objects.equals(objectPrefix, candidate.objectPrefix())
            && Objects.equals(pmcVersion, candidate.pmcVersion());
    boolean parsedByOlderParser = parserVersion != null && parserVersion < currentParserVersion;
    boolean awaiting =
        switch (status) {
          case FETCHED -> true;
          case PARSED, PARSE_FAILED -> parsedByOlderParser;
          case UNAVAILABLE ->
              unavailableReason == FullTextUnavailableReason.NO_BODY && parsedByOlderParser;
          case FETCH_FAILED -> false;
          case null -> false;
        };
    return sameXml && awaiting;
  }
}
