package dev.patra.catalog.infra.batch.publication.update;

import dev.patra.catalog.domain.model.vo.publication.PublicationCompleteData;
import dev.patra.catalog.domain.model.vo.publication.PublicationId;
import dev.patra.catalog.domain.model.vo.publication.PublicationSyncTarget;
import dev.patra.catalog.domain.port.repository.PublicationSyncRepository;
import dev.patra.catalog.infra.batch.publication.PublicationImportResult;
import dev.patra.catalog.infra.batch.publication.PublicationImportResultMapper;
import dev.patra.catalog.infra.batch.publication.PublicationItemWriter;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
  private final PubmedUpdateCounters counters;
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
      PubmedUpdateCounters counters,
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

    List<PublicationCompleteData> overwrites = new ArrayList<>();
    Map<Long, String> currentDois = new HashMap<>();
    List<PublicationImportResult> inserts = new ArrayList<>();
    for (PublicationSyncItem item : byPmid.values()) {
      PublicationSyncTarget target = item.target();
      if (target == null) {
        inserts.add(item.result());
        continue;
      }
      item.result().publication().assignId(PublicationId.of(target.publicationId()));
      overwrites.add(resultMapper.toCompleteData(item.result()));
      currentDois.put(target.publicationId(), target.doi());
    }

    // 2. 已有的：DOI 冲突时保留旧值，然后覆盖
    int conflicts = 0;
    if (!overwrites.isEmpty()) {
      Map<Long, String> doiOverrides = resolveDoiConflicts(overwrites, currentDois);
      conflicts = doiOverrides.size();
      syncRepository.overwriteAllWithAssociations(overwrites, doiOverrides, clock.instant());
    }

    // 3. 没有的（只在 ALL 范围出现）：委托 baseline 写入器
    int created = 0;
    if (!inserts.isEmpty()) {
      insertWriter.write(new Chunk<>(inserts));
      Set<String> pmids =
          inserts.stream().map(r -> r.publication().getPmid()).collect(Collectors.toSet());
      created = syncRepository.findSyncTargetsByPmids(pmids).size();
    }

    // 4. 全部成功后才计数
    counters.addUpdated(overwrites.size());
    counters.addCreated(created);
    counters.addDoiConflicts(conflicts);
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
