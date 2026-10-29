package dev.patra.catalog.infra.publication.batch.update;

import dev.patra.catalog.domain.publication.model.vo.PublicationCompleteData;
import dev.patra.catalog.domain.publication.model.vo.PublicationId;
import dev.patra.catalog.domain.publication.model.vo.PublicationSourceRow;
import dev.patra.catalog.domain.publication.model.vo.PublicationSyncTarget;
import dev.patra.catalog.domain.publication.port.repository.PublicationSyncRepository;
import dev.patra.catalog.infra.publication.batch.PublicationImportResult;
import dev.patra.catalog.infra.publication.batch.PublicationImportResultMapper;
import dev.patra.catalog.infra.publication.batch.PublicationItemWriter;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

/// 增量同步的写入器：已有的覆盖更新，没有的交给 baseline 的插入写入器。
///
/// 计数只在所有仓储调用成功之后才累加：Spring Batch 6 写入失败会回滚整个 chunk 并逐条重写，
/// 中途累加会重复计数。
///
/// @author linqibin
/// @since 0.11.0
@Slf4j
public class PublicationSyncItemWriter implements ItemWriter<PublicationSyncItem> {

  private final PublicationSyncRepository syncRepository;
  private final PublicationItemWriter insertWriter;
  private final PublicationImportResultMapper resultMapper;
  private final PubmedImportCounters counters;
  private final Clock clock;

  /// 创建写入器。
  ///
  /// @param syncRepository 覆盖更新仓储
  /// @param insertWriter baseline 的插入写入器（含 chunk 内与库内去重）
  /// @param resultMapper 处理结果到领域数据的转换
  /// @param counters 本 Step 的计数器
  /// @param clock 时钟，提供 `last_synced_at`
  public PublicationSyncItemWriter(
      PublicationSyncRepository syncRepository,
      PublicationItemWriter insertWriter,
      PublicationImportResultMapper resultMapper,
      PubmedImportCounters counters,
      Clock clock) {
    this.syncRepository = syncRepository;
    this.insertWriter = insertWriter;
    this.resultMapper = resultMapper;
    this.counters = counters;
    this.clock = clock;
  }

  /// 写入一个 chunk。
  ///
  /// @param chunk 同步项
  /// @throws Exception 仓储或插入写入器抛出的异常
  @Override
  public void write(Chunk<? extends PublicationSyncItem> chunk) throws Exception {
    // 1. chunk 内按 PMID 去重，后出现的为准
    Map<String, PublicationSyncItem> byPmid = new LinkedHashMap<>();
    for (PublicationSyncItem item : chunk.getItems()) {
      byPmid.remove(item.result().publication().getPmid());
      byPmid.put(item.result().publication().getPmid(), item);
    }

    Instant now = clock.instant();
    List<PublicationCompleteData> overwrites = new ArrayList<>();
    Map<Long, String> currentDois = new HashMap<>();
    Set<Long> refreshSyncedAt = new HashSet<>();
    List<PublicationSourceRow> sources = new ArrayList<>();
    List<PublicationImportResult> inserts = new ArrayList<>();
    Map<String, PublicationSyncItem> insertItems = new HashMap<>();
    for (PublicationSyncItem item : byPmid.values()) {
      PublicationSyncTarget target = item.target();
      if (target == null) {
        inserts.add(item.result());
        insertItems.put(item.result().publication().getPmid(), item);
        continue;
      }
      item.result().publication().assignId(PublicationId.of(target.publicationId()));
      overwrites.add(resultMapper.toCompleteData(item.result()));
      currentDois.put(target.publicationId(), target.doi());
      if (target.sourceRank() != null && item.position().rank() > target.sourceRank()) {
        refreshSyncedAt.add(target.publicationId());
      }
      sources.add(sourceRow(item, now));
    }

    // 2. 已有的：DOI 冲突时保留旧值，然后覆盖；只有拿到真正更新的版本才刷新 last_synced_at
    int conflicts = 0;
    if (!overwrites.isEmpty()) {
      Map<Long, String> doiOverrides = resolveDoiConflicts(overwrites, currentDois);
      conflicts = doiOverrides.size();
      syncRepository.overwriteAllWithAssociations(overwrites, doiOverrides, now, refreshSyncedAt);
    }

    // 3. 没有的：委托插入写入器；它会静默丢掉 DOI 已存在或重复的项，回查哪些真的进了库（墓碑不算）
    int created = 0;
    if (!inserts.isEmpty()) {
      insertWriter.write(new Chunk<>(inserts));
      Map<String, PublicationSyncTarget> found =
          syncRepository.findSyncTargetsByPmids(insertItems.keySet());
      for (Map.Entry<String, PublicationSyncTarget> e : found.entrySet()) {
        if (!e.getValue().isTombstone()) {
          created++;
          sources.add(sourceRow(insertItems.get(e.getKey()), now));
        }
      }
    }

    // 来源行与文献在同一个 chunk 事务里写
    if (!sources.isEmpty()) {
      syncRepository.upsertSources(sources);
    }

    // 4. 全部成功后才计数
    counters.addUpdated(overwrites.size());
    counters.addCreated(created);
    counters.addDoiConflicts(conflicts);
  }

  /// 写入项 → 来源行。
  ///
  /// @param item 写入项
  /// @param parsedAt 解析写入时间
  /// @return 来源行
  private static PublicationSourceRow sourceRow(PublicationSyncItem item, Instant parsedAt) {
    return PublicationSourceRow.builder()
        .pmid(item.result().publication().getPmid())
        .sourceFile(item.position().fileName())
        .sourceRank(item.position().rank())
        .rawXml(item.rawXml())
        .parsedAt(parsedAt)
        .build();
  }

  /// 找出 DOI 冲突的项，返回文献 ID → 旧 DOI。
  ///
  /// 两种冲突都保留旧 DOI：新 DOI 在库里已属于别的文献；同一 chunk 里已有排在前面的项认领了同一个新 DOI
  /// （否则第二条 UPDATE 撞 `uk_doi`，整个 chunk 失败且重跑仍会撞上）。
  private Map<Long, String> resolveDoiConflicts(
      List<PublicationCompleteData> overwrites, Map<Long, String> currentDois) {
    Map<Long, String> newDois = new LinkedHashMap<>();
    for (PublicationCompleteData data : overwrites) {
      String doi = data.publication().getDoi();
      if (doi != null && !doi.isBlank()) {
        newDois.put(data.getPublicationId(), doi.trim());
      }
    }
    if (newDois.isEmpty()) {
      return Map.of();
    }

    Map<String, Long> owners =
        syncRepository.findPublicationIdsByDois(Set.copyOf(newDois.values()));
    Map<Long, String> overrides = new HashMap<>();
    Set<String> claimed = new HashSet<>();
    newDois.forEach(
        (publicationId, doi) -> {
          Long owner = owners.get(doi);
          boolean ownedByOther = owner != null && !owner.equals(publicationId);
          if (ownedByOther || !claimed.add(doi)) {
            String oldDoi = currentDois.get(publicationId);
            log.warn(
                "新 DOI 已属于另一篇文献或被同批记录认领，保留旧 DOI：publicationId={}, newDoi={}, owner={}, oldDoi={}",
                publicationId,
                doi,
                owner,
                oldDoi);
            overrides.put(publicationId, oldDoi);
          }
        });
    return overrides;
  }
}
