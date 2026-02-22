package com.patra.catalog.infra.batch.publication;

import static com.patra.common.util.StringUtils.trimToNull;

import com.patra.catalog.domain.model.vo.publication.ExistingPublicationKeys;
import com.patra.catalog.domain.model.vo.publication.PublicationCompleteData;
import com.patra.catalog.domain.port.repository.PublicationRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

/// Publication 批量写入器。
///
/// **职责**：
///
/// - 将 Processor 处理结果映射为 Domain 数据对象
/// - 在 Writer 阶段做 chunk 内去重（PMID/DOI，first-win）
/// - 批量查询数据库已存在 PMID/DOI 并过滤
/// - 将最终结果委托 PublicationRepository 批量落库
///
/// **六边形架构改进**：
///
/// 重构前：Writer 直接依赖 15+ 个 DAO，违反了六边形架构原则
/// 重构后：Writer 仅依赖 PublicationRepository 和 Mapper，职责清晰
///
/// **写入流程**：
///
/// 1. 使用 Mapper 将 PublicationImportResult 转换为 PublicationCompleteData
/// 2. 在 chunk 内按 PMID/DOI 去重（保留首条）
/// 3. 批量查询数据库已存在 PMID/DOI 并过滤
/// 4. 委托 Repository 批量写入主数据和所有关联数据
///
/// **性能优化**：
///
/// - 批量插入减少数据库往返次数
/// - chunk size 由 Job 配置决定（推荐 500）
///
/// **错误处理**：
///
/// 由 Spring Batch FaultTolerant 机制处理：
/// - 单条失败时跳过该记录
/// - 批量失败时回退到逐条处理
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@RequiredArgsConstructor
public class PublicationItemWriter implements ItemWriter<PublicationImportResult> {

  private final PublicationRepository publicationRepository;
  private final PublicationImportResultMapper resultMapper;
  private final String importBatch;

  @Override
  public void write(Chunk<? extends PublicationImportResult> chunk) throws Exception {
    if (chunk.isEmpty()) {
      return;
    }

    List<PublicationCompleteData> data =
        chunk.getItems().stream().map(resultMapper::toCompleteData).toList();
    List<PublicationCompleteData> deduplicated = deduplicateWithinChunk(data);
    if (deduplicated.isEmpty()) {
      log.debug("当前 chunk 全部被去重过滤：input={}, importBatch={}", data.size(), importBatch);
      return;
    }

    List<PublicationCompleteData> toWrite = filterExistingInDb(deduplicated);
    if (toWrite.isEmpty()) {
      log.debug("当前 chunk 全部为数据库已存在记录：input={}, importBatch={}", data.size(), importBatch);
      return;
    }

    publicationRepository.insertAllWithAssociations(toWrite);

    log.debug(
        "批量写入 Publication：input={}, chunkDeduped={}, dbFiltered={}, importBatch={}",
        data.size(),
        deduplicated.size(),
        toWrite.size(),
        importBatch);
  }

  /// 在 chunk 范围内去重（PMID/DOI，first-win）。
  private List<PublicationCompleteData> deduplicateWithinChunk(List<PublicationCompleteData> data) {
    Set<String> seenPmids = new HashSet<>();
    Set<String> seenDois = new HashSet<>();
    List<PublicationCompleteData> filtered = new ArrayList<>(data.size());

    for (PublicationCompleteData item : data) {
      String pmid = normalizePmid(item.publication().getPmid());
      String doi = normalizeDoi(item.publication().getDoi());

      if ((pmid != null && seenPmids.contains(pmid)) || (doi != null && seenDois.contains(doi))) {
        log.warn(
            "跳过重复文献：PMID={}, DOI={}, importBatch={}, reason=IN_CHUNK_DUPLICATE",
            pmid,
            doi,
            importBatch);
        continue;
      }

      filtered.add(item);
      if (pmid != null) {
        seenPmids.add(pmid);
      }
      if (doi != null) {
        seenDois.add(doi);
      }
    }

    return filtered;
  }

  /// 过滤数据库中已存在的 PMID/DOI。
  private List<PublicationCompleteData> filterExistingInDb(List<PublicationCompleteData> data) {
    Set<String> pmids =
        data.stream()
            .map(item -> normalizePmid(item.publication().getPmid()))
            .filter(value -> value != null)
            .collect(java.util.stream.Collectors.toSet());
    Set<String> dois =
        data.stream()
            .map(item -> normalizeDoi(item.publication().getDoi()))
            .filter(value -> value != null)
            .collect(java.util.stream.Collectors.toSet());

    ExistingPublicationKeys existingKeys = publicationRepository.findExistingKeys(pmids, dois);
    if (existingKeys.pmids().isEmpty() && existingKeys.dois().isEmpty()) {
      return data;
    }

    List<PublicationCompleteData> filtered = new ArrayList<>(data.size());
    for (PublicationCompleteData item : data) {
      String pmid = normalizePmid(item.publication().getPmid());
      String doi = normalizeDoi(item.publication().getDoi());

      if ((pmid != null && existingKeys.pmids().contains(pmid))
          || (doi != null && existingKeys.dois().contains(doi))) {
        log.warn(
            "跳过重复文献：PMID={}, DOI={}, importBatch={}, reason=DB_DUPLICATE", pmid, doi, importBatch);
        continue;
      }
      filtered.add(item);
    }
    return filtered;
  }

  /// 标准化 PMID（trim）。
  private String normalizePmid(String pmid) {
    return trimToNull(pmid);
  }

  /// 标准化 DOI（trim + lower）。
  private String normalizeDoi(String doi) {
    String value = trimToNull(doi);
    if (value == null) {
      return null;
    }
    return value.toLowerCase(Locale.ROOT);
  }
}
