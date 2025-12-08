package com.patra.catalog.app.usecase.serfile;

import cn.hutool.core.collection.ListUtil;
import com.patra.catalog.api.error.CatalogErrorCode;
import com.patra.catalog.app.usecase.serfile.command.SerfileImportCommand;
import com.patra.catalog.app.usecase.serfile.dto.SerfileImportResult;
import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.dto.serfile.SerialIndexingHistory;
import com.patra.catalog.domain.model.dto.serfile.SerialLanguage;
import com.patra.catalog.domain.model.dto.serfile.SerialMeshHeading;
import com.patra.catalog.domain.model.dto.serfile.SerialRecord;
import com.patra.catalog.domain.model.dto.serfile.SerialTitleRelated;
import com.patra.catalog.domain.model.entity.VenueIndexingHistory;
import com.patra.catalog.domain.model.entity.VenueMesh;
import com.patra.catalog.domain.model.entity.VenueRelation;
import com.patra.catalog.domain.model.enums.CitationSubset;
import com.patra.catalog.domain.model.enums.IndexingTreatment;
import com.patra.catalog.domain.model.enums.VenueRelationType;
import com.patra.catalog.domain.model.vo.venue.PublicationHistory;
import com.patra.catalog.domain.model.vo.venue.VenueLanguages;
import com.patra.catalog.domain.port.parser.SerfileParserPort;
import com.patra.catalog.domain.port.repository.VenueRepository;
import com.patra.catalog.domain.port.repository.VenueSupplementRepository;
import com.patra.catalog.domain.port.source.StreamingDownloadPort;
import com.patra.catalog.domain.port.source.StreamingDownloadResult;
import com.patra.common.error.ApplicationException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/// NLM Serfile 数据导入编排器。
///
/// **职责**：
///
/// - 编排 Serfile 数据导入流程
/// - 管理事务边界（批量操作）
/// - 委派具体任务给领域端口
///
/// **导入策略**：
///
/// 增量覆盖模式：
///
/// 1. 匹配已有期刊记录时，PubMed 数据完全覆盖 OpenAlex 数据
/// 2. 匹配策略：ISSN-L → NLM ID → ISSN（降级策略）
/// 3. PubMed 独有期刊将创建新的 VenueAggregate 记录
///
/// **批处理设计**：
///
/// - 解析后的记录按批次处理（默认 500 条/批）
/// - 每批次内的操作在同一事务中完成
/// - 批次间提交，避免单事务过大
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Service
@RequiredArgsConstructor
public class SerfileImportOrchestrator implements SerfileImportUseCase {

  /// 批处理大小
  private static final int BATCH_SIZE = 500;

  private final StreamingDownloadPort streamingDownloadPort;
  private final SerfileParserPort parserPort;
  private final VenueRepository venueRepository;
  private final VenueSupplementRepository supplementRepository;
  private final TransactionTemplate transactionTemplate;

  /// 执行 NLM Serfile 期刊数据导入。
  ///
  /// **事务策略**：批次级别事务，每 500 条记录独立提交。
  /// 失败时仅回滚当前批次，已完成批次不受影响。
  ///
  /// **流式处理特性**：
  ///
  /// - 无磁盘落盘，HTTP 响应体直接传递给 Parser
  /// - 使用 try-with-resources 自动管理 HTTP 连接
  ///
  /// @param command 导入命令（包含 URL 和版本号）
  /// @return 导入结果摘要
  @Override
  public SerfileImportResult importSerfile(SerfileImportCommand command) {
    long startTime = System.currentTimeMillis();
    log.info("启动 Serfile 导入，URL：{}，版本：{}", command.url(), command.serfileVersion());

    // 流式下载并解析（无磁盘落盘）
    try (StreamingDownloadResult downloadResult =
        streamingDownloadPort.download(URI.create(command.url()))) {

      log.info("HTTP 连接建立成功，开始流式解析");

      // 解析并处理记录
      List<SerialRecord> allRecords = parserPort.parse(downloadResult.inputStream()).toList();
      log.info("解析完成，记录数：{}", allRecords.size());

      if (allRecords.isEmpty()) {
        long duration = System.currentTimeMillis() - startTime;
        return SerfileImportResult.success(
            0, 0, 0, 0, command.serfileVersion(), command.url(), duration);
      }

      // 按批次处理（每批次独立事务）
      AtomicInteger updatedCount = new AtomicInteger(0);
      AtomicInteger createdCount = new AtomicInteger(0);
      AtomicInteger skippedCount = new AtomicInteger(0);

      List<List<SerialRecord>> batches = ListUtil.partition(allRecords, BATCH_SIZE);
      int batchIndex = 0;
      for (List<SerialRecord> batch : batches) {
        int currentBatch = ++batchIndex;
        transactionTemplate.executeWithoutResult(
            status -> {
              log.debug("处理批次 {}/{}，记录数：{}", currentBatch, batches.size(), batch.size());
              processBatch(batch, updatedCount, createdCount, skippedCount);
            });
      }

      long duration = System.currentTimeMillis() - startTime;
      log.info(
          "Serfile 导入完成：解析 {} 条，更新 {} 条，新建 {} 条，跳过 {} 条，耗时 {} ms",
          allRecords.size(),
          updatedCount.get(),
          createdCount.get(),
          skippedCount.get(),
          duration);

      return SerfileImportResult.success(
          allRecords.size(),
          updatedCount.get(),
          createdCount.get(),
          skippedCount.get(),
          command.serfileVersion(),
          command.url(),
          duration);

    } catch (ApplicationException e) {
      throw e;
    } catch (RuntimeException e) {
      log.error("Serfile 导入失败：{}", command.url(), e);
      throw new ApplicationException(
          CatalogErrorCode.CAT_1003, "Serfile 导入失败: " + e.getMessage(), e);
    } catch (Exception e) {
      log.error("Serfile 导入时发生意外错误：{}", command.url(), e);
      throw new ApplicationException(
          CatalogErrorCode.CAT_1003, "Serfile 导入时发生意外错误: " + e.getMessage(), e);
    }
    // 无需 finally 清理临时文件，try-with-resources 自动关闭 HTTP 连接
  }

  /// 处理一个批次的记录。
  private void processBatch(
      List<SerialRecord> batch,
      AtomicInteger updatedCount,
      AtomicInteger createdCount,
      AtomicInteger skippedCount) {

    // 1. 收集所有标识符
    Set<String> issnLs = new HashSet<>();
    Set<String> nlmIds = new HashSet<>();
    Set<String> issns = new HashSet<>();

    for (SerialRecord record : batch) {
      if (record.issnL() != null && !record.issnL().isBlank()) {
        issnLs.add(record.issnL());
      }
      if (record.nlmUniqueId() != null && !record.nlmUniqueId().isBlank()) {
        nlmIds.add(record.nlmUniqueId());
      }
      if (record.issnPrint() != null && !record.issnPrint().isBlank()) {
        issns.add(record.issnPrint());
      }
      if (record.issnElectronic() != null && !record.issnElectronic().isBlank()) {
        issns.add(record.issnElectronic());
      }
    }

    // 2. 批量查询现有记录
    Map<String, VenueAggregate> byIssnL = venueRepository.findByIssnLs(issnLs);
    Map<String, VenueAggregate> byNlmId = venueRepository.findByNlmIds(nlmIds);
    Map<String, VenueAggregate> byIssn = venueRepository.findByIssns(issns);

    // 3. 匹配并分类，同时收集子实体数据
    List<VenueAggregate> toUpdate = new ArrayList<>();
    List<VenueAggregate> toCreate = new ArrayList<>();
    Set<Long> processedVenueIds = new HashSet<>(); // 避免同一期刊被多次更新

    // 收集子实体数据（以聚合根为键，待持久化后转换为 venueId）
    Map<VenueAggregate, List<VenueMesh>> meshByAggregate = new HashMap<>();
    Map<VenueAggregate, List<VenueRelation>> relationsByAggregate = new HashMap<>();
    Map<VenueAggregate, List<VenueIndexingHistory>> historiesByAggregate = new HashMap<>();

    for (SerialRecord record : batch) {
      Optional<VenueAggregate> matched = findMatch(record, byIssnL, byNlmId, byIssn);

      if (matched.isPresent()) {
        VenueAggregate venue = matched.get();
        // 避免同一期刊被多条记录更新
        if (!processedVenueIds.contains(venue.getId())) {
          updateVenueFromRecord(venue, record);
          toUpdate.add(venue);
          processedVenueIds.add(venue.getId());
          updatedCount.incrementAndGet();

          // 收集子实体数据
          meshByAggregate.put(venue, toVenueMeshList(record.meshHeadings()));
          relationsByAggregate.put(venue, toVenueRelationList(record.titleRelations()));
          historiesByAggregate.put(venue, toVenueIndexingHistoryList(record.indexingHistories()));
        } else {
          skippedCount.incrementAndGet();
        }
      } else {
        VenueAggregate newVenue = createVenueFromRecord(record);
        toCreate.add(newVenue);
        createdCount.incrementAndGet();

        // 收集子实体数据
        meshByAggregate.put(newVenue, toVenueMeshList(record.meshHeadings()));
        relationsByAggregate.put(newVenue, toVenueRelationList(record.titleRelations()));
        historiesByAggregate.put(newVenue, toVenueIndexingHistoryList(record.indexingHistories()));
      }
    }

    // 4. 批量持久化聚合根
    if (!toUpdate.isEmpty()) {
      venueRepository.updateBatch(toUpdate);
      log.debug("批量更新 {} 条记录", toUpdate.size());
    }
    if (!toCreate.isEmpty()) {
      venueRepository.insertAll(toCreate); // insertAll 会通过 assignId() 回填 ID
      log.debug("批量创建 {} 条记录", toCreate.size());
    }

    // 5. 构建 venueId -> 子实体列表 的映射并批量保存
    Map<Long, List<VenueMesh>> meshByVenueId = new HashMap<>();
    Map<Long, List<VenueRelation>> relationsByVenueId = new HashMap<>();
    Map<Long, List<VenueIndexingHistory>> historiesByVenueId = new HashMap<>();

    for (Map.Entry<VenueAggregate, List<VenueMesh>> entry : meshByAggregate.entrySet()) {
      Long venueId = entry.getKey().getId();
      if (venueId != null && !entry.getValue().isEmpty()) {
        meshByVenueId.put(venueId, entry.getValue());
      }
    }
    for (Map.Entry<VenueAggregate, List<VenueRelation>> entry : relationsByAggregate.entrySet()) {
      Long venueId = entry.getKey().getId();
      if (venueId != null && !entry.getValue().isEmpty()) {
        relationsByVenueId.put(venueId, entry.getValue());
      }
    }
    for (Map.Entry<VenueAggregate, List<VenueIndexingHistory>> entry :
        historiesByAggregate.entrySet()) {
      Long venueId = entry.getKey().getId();
      if (venueId != null && !entry.getValue().isEmpty()) {
        historiesByVenueId.put(venueId, entry.getValue());
      }
    }

    // 批量保存子实体
    supplementRepository.replaceSerfileDataBatch(
        meshByVenueId, relationsByVenueId, historiesByVenueId);
    log.debug(
        "批量保存子实体：MeSH {} 组，关系 {} 组，索引历史 {} 组",
        meshByVenueId.size(),
        relationsByVenueId.size(),
        historiesByVenueId.size());
  }

  /// 按优先级匹配期刊。
  ///
  /// 优先级：ISSN-L → NLM ID → ISSN
  private Optional<VenueAggregate> findMatch(
      SerialRecord record,
      Map<String, VenueAggregate> byIssnL,
      Map<String, VenueAggregate> byNlmId,
      Map<String, VenueAggregate> byIssn) {

    // 优先级 1: ISSN-L
    if (record.issnL() != null && byIssnL.containsKey(record.issnL())) {
      return Optional.of(byIssnL.get(record.issnL()));
    }

    // 优先级 2: NLM ID
    if (record.nlmUniqueId() != null && byNlmId.containsKey(record.nlmUniqueId())) {
      return Optional.of(byNlmId.get(record.nlmUniqueId()));
    }

    // 优先级 3: ISSN (Print)
    if (record.issnPrint() != null && byIssn.containsKey(record.issnPrint())) {
      return Optional.of(byIssn.get(record.issnPrint()));
    }

    // 优先级 3: ISSN (Electronic)
    if (record.issnElectronic() != null && byIssn.containsKey(record.issnElectronic())) {
      return Optional.of(byIssn.get(record.issnElectronic()));
    }

    return Optional.empty();
  }

  /// 从 SerialRecord 更新 VenueAggregate。
  ///
  /// PubMed 数据完全覆盖现有数据。
  /// **注意**：子实体（MeSH、关系、索引历史）通过 VenueSupplementRepository 单独保存。
  private void updateVenueFromRecord(VenueAggregate venue, SerialRecord record) {
    // 覆盖主表字段
    venue
        .withNlmId(record.nlmUniqueId())
        .withCoden(record.coden())
        .withFrequency(record.frequency())
        .withAbbreviatedTitle(record.medlineTA())
        .withCountryCode(record.country());

    // 覆盖 ISSN-L（如果有）
    if (record.issnL() != null && !record.issnL().isBlank()) {
      venue.withIssnL(record.issnL());
    }

    // 覆盖语言信息
    if (!record.languages().isEmpty()) {
      venue.withPrimaryLanguage(record.getPrimaryLanguage());
      venue.withLanguages(toVenueLanguages(record.languages()));
    }

    // 覆盖出版历史
    if (record.publicationFirstYear() != null || record.publicationEndYear() != null) {
      venue.withPublicationHistory(
          PublicationHistory.of(
              record.publicationFirstYear(), record.publicationEndYear(), record.isCeased()));
    }
    // 子实体（meshTerms, relations, indexingHistories）在 processBatch() 中单独收集并保存
  }

  /// 从 SerialRecord 创建新 VenueAggregate。
  ///
  /// **注意**：子实体（MeSH、关系、索引历史）通过 VenueSupplementRepository 单独保存。
  private VenueAggregate createVenueFromRecord(SerialRecord record) {
    VenueAggregate venue =
        VenueAggregate.fromPubMed(record.title(), record.nlmUniqueId(), record.issnL());

    // 设置基本字段
    venue
        .withCoden(record.coden())
        .withFrequency(record.frequency())
        .withAbbreviatedTitle(record.medlineTA())
        .withCountryCode(record.country());

    // 设置语言信息
    if (!record.languages().isEmpty()) {
      venue.withPrimaryLanguage(record.getPrimaryLanguage());
      venue.withLanguages(toVenueLanguages(record.languages()));
    }

    // 设置出版历史
    if (record.publicationFirstYear() != null || record.publicationEndYear() != null) {
      venue.withPublicationHistory(
          PublicationHistory.of(
              record.publicationFirstYear(), record.publicationEndYear(), record.isCeased()));
    }
    // 子实体（meshTerms, relations, indexingHistories）在 processBatch() 中单独收集并保存

    return venue;
  }

  /// 转换语言列表为 VenueLanguages。
  ///
  /// 根据 LangType 将语言分类为主语言（Primary）和摘要语言（Summary）。
  private VenueLanguages toVenueLanguages(List<SerialLanguage> languages) {
    if (languages == null || languages.isEmpty()) {
      return VenueLanguages.empty();
    }
    List<String> primary =
        languages.stream().filter(SerialLanguage::isPrimary).map(SerialLanguage::code).toList();
    List<String> summary =
        languages.stream().filter(l -> !l.isPrimary()).map(SerialLanguage::code).toList();
    return VenueLanguages.of(primary, summary);
  }

  /// 转换 MeSH 主题词列表。
  private List<VenueMesh> toVenueMeshList(List<SerialMeshHeading> headings) {
    if (headings == null || headings.isEmpty()) {
      return List.of();
    }
    return headings.stream()
        .map(
            h -> {
              if (h.qualifierName() != null) {
                return VenueMesh.create(
                    h.descriptorName(), null, h.isMajorTopic(), h.qualifierName());
              } else {
                return VenueMesh.create(h.descriptorName(), null, h.isMajorTopic());
              }
            })
        .toList();
  }

  /// 转换期刊关联关系列表。
  private List<VenueRelation> toVenueRelationList(List<SerialTitleRelated> relations) {
    if (relations == null || relations.isEmpty()) {
      return List.of();
    }
    return relations.stream()
        .filter(r -> r.relatedTitle() != null && !r.relatedTitle().isBlank())
        .map(
            r -> {
              VenueRelationType type = VenueRelationType.fromSerfileTitleType(r.titleType());
              // 如果无法识别类型，默认使用 PRECEDING（最常见的关系类型）
              if (type == null) {
                type = VenueRelationType.PRECEDING;
                log.debug("无法识别的 TitleType：{}，默认使用 PRECEDING", r.titleType());
              }
              return VenueRelation.create(r.relatedTitle(), type);
            })
        .toList();
  }

  /// 转换索引历史列表。
  private List<VenueIndexingHistory> toVenueIndexingHistoryList(
      List<SerialIndexingHistory> histories) {
    if (histories == null || histories.isEmpty()) {
      return List.of();
    }
    return histories.stream()
        .map(
            h -> {
              IndexingTreatment treatment = parseIndexingTreatment(h.indexingTreatment());
              CitationSubset citationSubset = parseCitationSubset(h.citationSubset());

              if (h.isCurrentlyIndexed()) {
                return VenueIndexingHistory.createCurrentIndexing(
                    "MEDLINE", treatment, citationSubset, null, null, null);
              } else {
                return VenueIndexingHistory.createHistoricalIndexing(
                    "MEDLINE", null, null, null, null, null, null);
              }
            })
        .toList();
  }

  /// 解析 IndexingTreatment。
  private IndexingTreatment parseIndexingTreatment(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return IndexingTreatment.fromCode(value);
    } catch (IllegalArgumentException e) {
      log.warn("无法识别的 IndexingTreatment 值：{}", value);
      return null;
    }
  }

  /// 解析 CitationSubset。
  private CitationSubset parseCitationSubset(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return CitationSubset.fromCode(value);
    } catch (IllegalArgumentException e) {
      log.warn("无法识别的 CitationSubset 值：{}", value);
      return null;
    }
  }
}
