package dev.patra.catalog.app.fulltext.usecase.parse;

import dev.patra.catalog.domain.fulltext.model.enums.FullTextUnavailableReason;
import dev.patra.catalog.domain.fulltext.model.read.ParseCandidate;
import dev.patra.catalog.domain.fulltext.model.vo.PublicationFullText;
import dev.patra.catalog.domain.fulltext.model.vo.body.FullTextBody;
import dev.patra.catalog.domain.fulltext.port.parser.JatsBodyParserPort;
import dev.patra.catalog.domain.fulltext.port.repository.PublicationFullTextRepository;
import dev.patra.catalog.domain.kernel.model.enums.FullTextStatus;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// 解析结果的持久化，每篇一个 `REQUIRES_NEW` 事务。
///
/// 三种结果都写入当前解析器版本：成功与无正文在版本提升后重新解析，失败在版本提升前不再重试。
///
/// 解析在事务外读对象存储，抓取任务（另一把运行锁）可能同时重抓这篇。所以每个事务先锁住全文记录再重读，
/// 记录已不是当初那条候选（换了 XML 版本或状态）时整篇丢弃、返回 `false`，新内容留给下一轮解析。
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
  /// @return 写入时为 true；解析期间已被重抓、结果丢弃时为 false
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public boolean recordParsed(ParseCandidate c, FullTextBody body, Instant parsedAt) {
    Optional<PublicationFullText> current = lockIfStillAwaiting(c);
    if (current.isEmpty()) {
      return false;
    }
    repository.saveBody(c.publicationId(), body, JatsBodyParserPort.PARSER_VERSION);
    repository.save(
        current.get().toBuilder()
            .status(FullTextStatus.PARSED)
            .unavailableReason(null)
            .wordCount(body.wordCount())
            .parserVersion(JatsBodyParserPort.PARSER_VERSION)
            .parsedAt(parsedAt)
            .lastError(null)
            .build());
    repository.setHasFullText(c.publicationId(), true);
    return true;
  }

  /// 无正文：记录 `UNAVAILABLE(NO_BODY)`，PDF 仍可下载，`has_full_text = false`。
  ///
  /// @param c 候选
  /// @param parsedAt 解析时间
  /// @return 写入时为 true；解析期间已被重抓、结果丢弃时为 false
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public boolean recordNoBody(ParseCandidate c, Instant parsedAt) {
    Optional<PublicationFullText> current = lockIfStillAwaiting(c);
    if (current.isEmpty()) {
      return false;
    }
    repository.save(
        current.get().toBuilder()
            .status(FullTextStatus.UNAVAILABLE)
            .unavailableReason(FullTextUnavailableReason.NO_BODY)
            .wordCount(null)
            .parserVersion(JatsBodyParserPort.PARSER_VERSION)
            .parsedAt(parsedAt)
            .lastError(null)
            .nextAttemptAt(null)
            .build());
    repository.setHasFullText(c.publicationId(), false);
    return true;
  }

  /// 解析失败：记录 `PARSE_FAILED` 与原因，`has_full_text = false`。
  ///
  /// @param c 候选
  /// @param error 原因
  /// @param parsedAt 解析时间
  /// @return 写入时为 true；解析期间已被重抓、结果丢弃时为 false
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public boolean recordParseFailure(ParseCandidate c, Exception error, Instant parsedAt) {
    Optional<PublicationFullText> current = lockIfStillAwaiting(c);
    if (current.isEmpty()) {
      return false;
    }
    String message = error.getClass().getSimpleName() + ": " + error.getMessage();
    repository.save(
        current.get().toBuilder()
            .status(FullTextStatus.PARSE_FAILED)
            .parserVersion(JatsBodyParserPort.PARSER_VERSION)
            .parsedAt(parsedAt)
            .lastError(
                message.length() <= MAX_ERROR_LENGTH
                    ? message
                    : message.substring(0, MAX_ERROR_LENGTH))
            .build());
    repository.setHasFullText(c.publicationId(), false);
    return true;
  }

  /// 锁住全文记录并重读；已不是这条候选（解析期间被重抓）时返回空，调用方丢弃结果。
  ///
  /// 候选来自全文表，记录必然存在。
  private Optional<PublicationFullText> lockIfStillAwaiting(ParseCandidate c) {
    PublicationFullText current =
        repository
            .lockByPublicationId(c.publicationId())
            .orElseThrow(
                () -> new IllegalStateException("全文记录不存在：publicationId=" + c.publicationId()));
    return current.stillAwaitsParsing(c, JatsBodyParserPort.PARSER_VERSION)
        ? Optional.of(current)
        : Optional.empty();
  }
}
