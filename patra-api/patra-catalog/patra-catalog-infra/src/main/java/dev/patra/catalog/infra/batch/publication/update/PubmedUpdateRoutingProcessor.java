package dev.patra.catalog.infra.batch.publication.update;

import dev.patra.catalog.domain.model.enums.PubmedUpdateScope;
import dev.patra.catalog.domain.model.vo.publication.PublicationSyncTarget;
import dev.patra.catalog.domain.port.repository.PublicationSyncRepository;
import dev.patra.catalog.infra.batch.publication.PublicationImportResult;
import dev.patra.catalog.infra.batch.publication.PubmedArticleItemProcessor;
import dev.patra.common.CanonicalPublication;
import dev.patra.common.CanonicalPublication.Identifier;
import dev.patra.common.PublicationIdentifierType;
import java.util.Set;
import org.springframework.batch.infrastructure.item.ItemProcessor;

/// 增量同步的分流处理器：在调用 baseline 处理器之前按 PMID 决定覆盖、新建还是跳过。
///
/// 分流必须在委托之前：baseline 处理器会用 `REQUIRES_NEW` 事务查找或创建期刊实例，
/// `EXISTING_ONLY` 下若先委托再判断，库里没有的记录也会留下期刊实例行。
///
/// @author linqibin
/// @since 0.11.0
public class PubmedUpdateRoutingProcessor
    implements ItemProcessor<CanonicalPublication, PublicationSyncItem> {

  private final PublicationSyncRepository syncRepository;
  private final PubmedArticleItemProcessor delegate;
  private final PubmedUpdateScope scope;
  private final PubmedUpdateCounters counters;

  /// 创建分流处理器。
  ///
  /// @param syncRepository 覆盖更新仓储（查同步目标）
  /// @param delegate baseline 处理器
  /// @param scope 导入范围
  /// @param counters 本 Step 的计数器
  public PubmedUpdateRoutingProcessor(
      PublicationSyncRepository syncRepository,
      PubmedArticleItemProcessor delegate,
      PubmedUpdateScope scope,
      PubmedUpdateCounters counters) {
    this.syncRepository = syncRepository;
    this.delegate = delegate;
    this.scope = scope;
    this.counters = counters;
  }

  /// 分流一条记录。
  ///
  /// @param publication 解析出的记录
  /// @return 同步项；跳过时为 `null`
  /// @throws Exception 委托处理器抛出的异常
  @Override
  public PublicationSyncItem process(CanonicalPublication publication) throws Exception {
    String pmid = extractPmid(publication);
    if (pmid == null) {
      counters.incrementSkippedInvalid();
      return null;
    }

    PublicationSyncTarget target = syncRepository.findSyncTargetsByPmids(Set.of(pmid)).get(pmid);
    if (target == null && scope == PubmedUpdateScope.EXISTING_ONLY) {
      counters.incrementSkippedAbsent();
      return null;
    }

    PublicationImportResult result = delegate.process(publication);
    if (result == null) {
      counters.incrementSkippedInvalid();
      return null;
    }
    return PublicationSyncItem.of(result, target);
  }

  /// 取 PMID，空白视为没有。
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
}
