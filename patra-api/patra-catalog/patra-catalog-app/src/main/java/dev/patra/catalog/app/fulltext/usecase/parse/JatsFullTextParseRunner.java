package dev.patra.catalog.app.fulltext.usecase.parse;

import dev.patra.catalog.app.fulltext.config.FullTextJobProperties;
import dev.patra.catalog.domain.fulltext.model.read.ParseCandidate;
import dev.patra.catalog.domain.fulltext.model.vo.JatsParseResult;
import dev.patra.catalog.domain.fulltext.model.vo.StoredFile;
import dev.patra.catalog.domain.fulltext.port.parser.JatsBodyParserPort;
import dev.patra.catalog.domain.fulltext.port.read.FullTextCandidateReadPort;
import dev.patra.catalog.domain.fulltext.port.storage.FullTextObjectStoragePort;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 全文解析：keyset 分批取候选，从对象存储读 XML 解析，处理到没有候选为止。全程不访问 PMC。
///
/// 读存储出错不算解析失败：不写记录、保持原状态，下次运行再试；解析器抛出的异常记 `PARSE_FAILED`。
///
/// @author linqibin
/// @since 0.11.0
@Slf4j
@Component
@RequiredArgsConstructor
public class JatsFullTextParseRunner {

  private final FullTextCandidateReadPort candidatePort;
  private final FullTextObjectStoragePort storage;
  private final JatsBodyParserPort parser;
  private final JatsFullTextParsePersister persister;
  private final FullTextJobProperties properties;
  private final Clock clock;

  /// 执行一次解析。
  ///
  /// @return 统计
  public FullTextParseRunStats run() {
    FullTextParseRunStats stats = FullTextParseRunStats.empty();
    long lastId = 0L;
    while (!Thread.currentThread().isInterrupted()) {
      List<ParseCandidate> batch =
          candidatePort.findParseCandidates(
              JatsBodyParserPort.PARSER_VERSION, lastId, properties.parse().batchSize());
      if (batch.isEmpty()) {
        break;
      }
      for (ParseCandidate c : batch) {
        stats = process(c, stats);
      }
      lastId = batch.getLast().publicationId();
      log.info("全文解析进度：{}，游标 {}", stats, lastId);
    }
    return stats;
  }

  /// 处理一篇。
  private FullTextParseRunStats process(ParseCandidate c, FullTextParseRunStats stats) {
    StoredFile file;
    try {
      file = storage.open(c.xmlObjectKey());
    } catch (RuntimeException e) {
      log.warn("读取全文 XML 失败，保持原状态待下次再试：publicationId={}，{}", c.publicationId(), e.toString());
      return stats.plusFailed();
    }
    JatsParseResult result;
    try (file) {
      result = parser.parse(file.content(), c.media());
    } catch (IOException e) {
      log.warn("关闭全文 XML 流失败：publicationId={}", c.publicationId(), e);
      return stats.plusFailed();
    } catch (RuntimeException e) {
      if (causedByIo(e)) {
        log.warn("读取全文 XML 中途出错，保持原状态待下次再试：publicationId={}，{}", c.publicationId(), e.toString());
        return stats.plusFailed();
      }
      log.warn("全文解析失败：publicationId={}，{}", c.publicationId(), e.toString());
      persister.recordParseFailure(c, e, clock.instant());
      return stats.plusFailed();
    }
    if (result.noBody()) {
      persister.recordNoBody(c, clock.instant());
      return stats.plusNoBody();
    }
    persister.recordParsed(c, result.body(), clock.instant());
    return stats.plusParsed();
  }

  /// 异常链上是否有 IOException（解析中途读流出错会被 SAX 包装成解析异常）。
  private static boolean causedByIo(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof IOException) {
        return true;
      }
    }
    return false;
  }
}
