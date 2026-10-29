package dev.patra.catalog.app.fulltext.usecase.parse;

import dev.patra.catalog.domain.fulltext.model.enums.FullTextUnavailableReason;
import dev.patra.catalog.domain.fulltext.model.read.ParseCandidate;
import dev.patra.catalog.domain.fulltext.model.vo.PublicationFullText;
import dev.patra.catalog.domain.fulltext.model.vo.body.FullTextBody;
import dev.patra.catalog.domain.fulltext.port.parser.JatsBodyParserPort;
import dev.patra.catalog.domain.fulltext.port.repository.PublicationFullTextRepository;
import dev.patra.catalog.domain.kernel.model.enums.FullTextStatus;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// 解析结果的持久化，每篇一个 `REQUIRES_NEW` 事务。
///
/// 三种结果都写入当前解析器版本：成功与无正文在版本提升后重新解析，失败在版本提升前不再重试。
///
/// @author linqibin
/// @since 0.11.0
@Component
@RequiredArgsConstructor
public class JatsFullTextParsePersister {

  private static final int MAX_ERROR_LENGTH = 2000;

  private final PublicationFullTextRepository repository;

  /// 解析成功：写正文，记录 `PARSED` 与字数，`has_full_text = true`。
  ///
  /// @param c 候选
  /// @param body 正文
  /// @param parsedAt 解析时间
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public void recordParsed(ParseCandidate c, FullTextBody body, Instant parsedAt) {
    repository.saveBody(c.publicationId(), body, JatsBodyParserPort.PARSER_VERSION);
    repository.save(
        current(c).toBuilder()
            .status(FullTextStatus.PARSED)
            .unavailableReason(null)
            .wordCount(body.wordCount())
            .parserVersion(JatsBodyParserPort.PARSER_VERSION)
            .parsedAt(parsedAt)
            .lastError(null)
            .build());
    repository.setHasFullText(c.publicationId(), true);
  }

  /// 无正文：记录 `UNAVAILABLE(NO_BODY)`，PDF 仍可下载，`has_full_text = false`。
  ///
  /// @param c 候选
  /// @param parsedAt 解析时间
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public void recordNoBody(ParseCandidate c, Instant parsedAt) {
    repository.save(
        current(c).toBuilder()
            .status(FullTextStatus.UNAVAILABLE)
            .unavailableReason(FullTextUnavailableReason.NO_BODY)
            .wordCount(null)
            .parserVersion(JatsBodyParserPort.PARSER_VERSION)
            .parsedAt(parsedAt)
            .lastError(null)
            .nextAttemptAt(null)
            .build());
    repository.setHasFullText(c.publicationId(), false);
  }

  /// 解析失败：记录 `PARSE_FAILED` 与原因，`has_full_text = false`。
  ///
  /// @param c 候选
  /// @param error 原因
  /// @param parsedAt 解析时间
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public void recordParseFailure(ParseCandidate c, Exception error, Instant parsedAt) {
    String message = error.getClass().getSimpleName() + ": " + error.getMessage();
    repository.save(
        current(c).toBuilder()
            .status(FullTextStatus.PARSE_FAILED)
            .parserVersion(JatsBodyParserPort.PARSER_VERSION)
            .parsedAt(parsedAt)
            .lastError(
                message.length() <= MAX_ERROR_LENGTH
                    ? message
                    : message.substring(0, MAX_ERROR_LENGTH))
            .build());
    repository.setHasFullText(c.publicationId(), false);
  }

  /// 当前全文记录（候选来自全文表，必然存在）。
  private PublicationFullText current(ParseCandidate c) {
    return repository
        .findByPublicationId(c.publicationId())
        .orElseThrow(() -> new IllegalStateException("全文记录不存在：publicationId=" + c.publicationId()));
  }
}
