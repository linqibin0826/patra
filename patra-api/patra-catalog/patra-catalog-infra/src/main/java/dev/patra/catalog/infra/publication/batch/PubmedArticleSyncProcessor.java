package dev.patra.catalog.infra.publication.batch;

import dev.patra.catalog.domain.kernel.exception.XmlParseException;
import dev.patra.catalog.domain.publication.model.enums.PubmedImportScope;
import dev.patra.catalog.domain.publication.model.vo.PublicationSyncTarget;
import dev.patra.catalog.domain.publication.model.vo.PubmedSourceRecord;
import dev.patra.catalog.domain.publication.model.vo.SourcePosition;
import dev.patra.catalog.domain.publication.port.parser.PubmedXmlParserPort;
import dev.patra.catalog.domain.publication.port.repository.PublicationSyncRepository;
import dev.patra.catalog.infra.publication.batch.update.PublicationSyncItem;
import dev.patra.catalog.infra.publication.batch.update.PubmedImportCounters;
import dev.patra.common.CanonicalPublication;
import dev.patra.common.CanonicalPublication.Identifier;
import dev.patra.common.PartialDate;
import dev.patra.common.PublicationIdentifierType;
import java.time.DateTimeException;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ItemProcessor;

/// 文件导入与本地重算共用的处理器：解析原文 → 按来源位置判断新旧 → 委托组装。
///
/// 守卫规则（spec 第 5.2 节）：库里没有位置视为最旧；进来的比库里旧就跳过（`skippedStale`）；同一位置覆盖
/// （按新解析器重算）；已软删或墓碑只有更新的版本才覆盖 / 新增；墓碑比进来的旧时当作库里没有。本地重算只接受
/// 同一位置的未软删文献。
///
/// 单篇的解析与领域断言类异常只跳过这一篇（`skippedInvalid`），数据库等基础设施异常照常抛出让文件失败。
/// 判断必须在委托之前：委托会用 `REQUIRES_NEW` 事务查找或创建期刊实例，跳过的记录不该留下期刊实例行。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
public class PubmedArticleSyncProcessor
    implements ItemProcessor<PubmedSourceRecord, PublicationSyncItem> {

  private final PubmedXmlParserPort parser;
  private final PublicationSyncRepository syncRepository;
  private final PubmedArticleItemProcessor delegate;
  private final PubmedImportScope scope;
  private final PubmedImportCounters counters;
  private final boolean reparse;

  /// 创建处理器。
  ///
  /// @param parser PubMed 解析端口
  /// @param syncRepository 覆盖更新仓储（查同步目标）
  /// @param delegate 组装处理器
  /// @param scope 导入范围
  /// @param counters 本 Step 的计数器
  /// @param reparse 是否本地重算（只接受同一位置的未软删文献）
  public PubmedArticleSyncProcessor(
      PubmedXmlParserPort parser,
      PublicationSyncRepository syncRepository,
      PubmedArticleItemProcessor delegate,
      PubmedImportScope scope,
      PubmedImportCounters counters,
      boolean reparse) {
    this.parser = parser;
    this.syncRepository = syncRepository;
    this.delegate = delegate;
    this.scope = scope;
    this.counters = counters;
    this.reparse = reparse;
  }

  /// 处理一篇。
  ///
  /// @param record 原文与来源位置
  /// @return 写入项；跳过时为 `null`
  /// @throws Exception 基础设施异常
  @Override
  public PublicationSyncItem process(PubmedSourceRecord record) throws Exception {
    CanonicalPublication publication;
    try {
      publication = parser.parseArticle(record.rawXml());
    } catch (XmlParseException
        | IllegalArgumentException
        | IllegalStateException
        | DateTimeException e) {
      return skipInvalid(record, "原文解析失败：" + e.getMessage());
    }
    String pmid = publication == null ? null : extractPmid(publication);
    if (pmid == null) {
      return skipInvalid(record, "缺少 PMID");
    }
    String missing = missingRequiredField(publication);
    if (missing != null) {
      return skipInvalid(record, missing);
    }

    PublicationSyncTarget target = syncRepository.findSyncTargetsByPmids(Set.of(pmid)).get(pmid);
    Decision decision = decide(target, record.position());
    switch (decision) {
      case SKIP_STALE -> {
        counters.incrementSkippedStale();
        return null;
      }
      case SKIP_ABSENT -> {
        counters.incrementSkippedAbsent();
        return null;
      }
      default -> {
        // INSERT / OVERWRITE 继续组装
      }
    }

    PublicationImportResult result;
    try {
      result = delegate.process(publication);
    } catch (IllegalArgumentException | IllegalStateException | DateTimeException e) {
      return skipInvalid(record, "组装失败：" + e.getMessage());
    }
    if (result == null) {
      return skipInvalid(record, "组装结果为空");
    }
    return PublicationSyncItem.of(
        result, decision == Decision.INSERT ? null : target, record.rawXml(), record.position());
  }

  /// 按守卫规则决定怎么处理。
  ///
  /// @param target 同步目标，可空
  /// @param position 进来的来源位置
  /// @return 决定
  private Decision decide(PublicationSyncTarget target, SourcePosition position) {
    if (reparse) {
      boolean sameLive =
          target != null
              && !target.isTombstone()
              && !target.deleted()
              && Integer.valueOf(position.rank()).equals(target.sourceRank());
      return sameLive ? Decision.OVERWRITE : Decision.SKIP_STALE;
    }
    if (target == null) {
      return absent();
    }
    Integer stored = target.sourceRank();
    boolean newer = stored == null || position.rank() > stored;
    if (target.isTombstone()) {
      return newer ? absent() : Decision.SKIP_STALE;
    }
    if (target.deleted()) {
      return newer ? Decision.OVERWRITE : Decision.SKIP_STALE;
    }
    return position.isOlderThan(stored) ? Decision.SKIP_STALE : Decision.OVERWRITE;
  }

  /// 库里没有时按范围决定：`ALL` 新增，`EXISTING_ONLY` 跳过。
  ///
  /// @return 决定
  private Decision absent() {
    return scope == PubmedImportScope.ALL ? Decision.INSERT : Decision.SKIP_ABSENT;
  }

  /// 检查必填字段：年份（1800 ~ 2100）与标题。
  ///
  /// @param publication 规范化文献
  /// @return 缺失或不合法时的说明；齐全时为 null
  private static String missingRequiredField(CanonicalPublication publication) {
    PartialDate published =
        publication.getDates() == null ? null : publication.getDates().getPublished();
    if (published == null) {
      return "缺少出版年份";
    }
    if (published.year() < 1800 || published.year() > 2100) {
      return "出版年份不在 1800 ~ 2100：" + published.year();
    }
    // 与组装处理器的标题容错一致：ArticleTitle 为空时用 VernacularTitle（非英文文献常见）
    if (isBlank(publication.getTitle()) && isBlank(publication.getOriginalTitle())) {
      return "缺少标题";
    }
    return null;
  }

  /// 跳过一篇无效记录并记日志。
  ///
  /// @param record 原文与来源位置
  /// @param reason 原因
  /// @return 恒为 `null`
  private PublicationSyncItem skipInvalid(PubmedSourceRecord record, String reason) {
    counters.incrementSkippedInvalid();
    log.warn(
        "跳过无效 PubMed 记录：pmid={}，file={}，{}", record.pmid(), record.position().fileName(), reason);
    return null;
  }

  /// 取 PMID，空白视为没有。
  ///
  /// @param publication 规范化文献
  /// @return PMID，可空
  private static String extractPmid(CanonicalPublication publication) {
    if (publication.getIdentifiers() == null) {
      return null;
    }
    return publication.getIdentifiers().stream()
        .filter(id -> PublicationIdentifierType.PMID == id.getType())
        .map(Identifier::getValue)
        .filter(value -> value != null && !value.isBlank())
        .map(String::trim)
        .findFirst()
        .orElse(null);
  }

  /// 守卫的决定。
  private enum Decision {
    /// 新增
    INSERT,
    /// 覆盖
    OVERWRITE,
    /// 比库里旧，跳过
    SKIP_STALE,
    /// 库里没有且范围不允许新增，跳过
    SKIP_ABSENT
  }

  /// 值是否为空或只含空白。
  ///
  /// @param value 值，可空
  /// @return 是否空白
  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
