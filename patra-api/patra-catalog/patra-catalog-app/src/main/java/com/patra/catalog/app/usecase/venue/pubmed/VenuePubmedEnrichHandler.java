package com.patra.catalog.app.usecase.venue.pubmed;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.TimeInterval;
import com.patra.catalog.api.error.CatalogErrorCode;
import com.patra.catalog.app.usecase.venue.pubmed.command.VenuePubmedEnrichCommand;
import com.patra.catalog.app.usecase.venue.pubmed.dto.VenuePubmedEnrichResult;
import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.enums.CitationSubset;
import com.patra.catalog.domain.model.enums.IndexingTreatment;
import com.patra.catalog.domain.model.enums.VenueIdentifierType;
import com.patra.catalog.domain.model.enums.VenueRelationType;
import com.patra.catalog.domain.model.vo.venue.IndexingInfo;
import com.patra.catalog.domain.model.vo.venue.PublicationHistory;
import com.patra.catalog.domain.model.vo.venue.PublicationProfile;
import com.patra.catalog.domain.model.vo.venue.VenueIdentifier;
import com.patra.catalog.domain.model.vo.venue.VenueIndexingHistory;
import com.patra.catalog.domain.model.vo.venue.VenueLanguages;
import com.patra.catalog.domain.model.vo.venue.VenueMesh;
import com.patra.catalog.domain.model.vo.venue.VenueRelation;
import com.patra.catalog.domain.model.vo.venue.pubmed.PubmedIndexingHistory;
import com.patra.catalog.domain.model.vo.venue.pubmed.PubmedLanguage;
import com.patra.catalog.domain.model.vo.venue.pubmed.PubmedMeshHeading;
import com.patra.catalog.domain.model.vo.venue.pubmed.PubmedSerialData;
import com.patra.catalog.domain.model.vo.venue.pubmed.PubmedTitleRelation;
import com.patra.catalog.domain.port.parser.SerfileParserPort;
import com.patra.catalog.domain.port.repository.VenueRepository;
import com.patra.catalog.domain.port.source.StreamingDownloadPort;
import com.patra.catalog.domain.port.source.StreamingDownloadResult;
import com.patra.common.cqrs.CommandHandler;
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
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/// PubMed Venue 数据富化编排器。
///
/// **职责**：
///
/// - 编排 PubMed 数据富化流程（基于 NLM Serfile）
/// - 管理事务边界（批量操作）
/// - 委派具体任务给领域端口
///
/// **富化策略**：
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
@Component
@RequiredArgsConstructor
public class VenuePubmedEnrichHandler
    implements CommandHandler<VenuePubmedEnrichCommand, VenuePubmedEnrichResult> {

  /// 批处理大小
  private static final int BATCH_SIZE = 500;

  private final StreamingDownloadPort streamingDownloadPort;
  private final SerfileParserPort parserPort;
  private final VenueRepository venueRepository;
  private final TransactionTemplate transactionTemplate;

  /// 执行 PubMed Venue 数据富化。
  ///
  /// **事务策略**：批次级别事务，每 500 条记录独立提交。
  /// 失败时仅回滚当前批次，已完成批次不受影响。
  ///
  /// **流式处理特性**：
  ///
  /// - 无磁盘落盘，HTTP 响应体直接传递给 Parser
  /// - 使用 try-with-resources 自动管理 HTTP 连接
  ///
  /// @param command 富化命令（包含 URL 和版本号）
  /// @return 富化结果摘要
  @Override
  public VenuePubmedEnrichResult handle(VenuePubmedEnrichCommand command) {
    TimeInterval timer = DateUtil.timer();
    log.info("启动 PubMed Venue 富化，URL：{}，版本：{}", command.url(), command.serfileVersion());

    // ────────────────────────────────────────────────────────────────────────────
    // 阶段 1：流式下载
    // - 使用 try-with-resources 管理 HTTP 连接生命周期
    // - 无磁盘落盘，HTTP 响应体直接传递给 Parser
    // ────────────────────────────────────────────────────────────────────────────
    try (StreamingDownloadResult downloadResult =
        streamingDownloadPort.download(URI.create(command.url()))) {

      log.info("HTTP 连接建立成功，开始流式解析");

      // ────────────────────────────────────────────────────────────────────────────
      // 阶段 2：XML 解析
      // - 将 NLM Serfile XML 解析为 PubmedSerialData 领域对象
      // - 一次性加载所有记录到内存（Serfile 约 3-4 万条，可承受）
      // ────────────────────────────────────────────────────────────────────────────
      List<PubmedSerialData> allRecords = parserPort.parse(downloadResult.inputStream()).toList();
      log.info("解析完成，记录数：{}", allRecords.size());

      if (allRecords.isEmpty()) {
        return VenuePubmedEnrichResult.success(
            0, 0, 0, 0, command.serfileVersion(), command.url(), timer.interval());
      }

      // ────────────────────────────────────────────────────────────────────────────
      // 阶段 3：分批事务处理
      // - 将全量记录切分为 500 条/批
      // - 每批次独立事务：批次内原子提交，批次间隔离
      // - 失败时仅回滚当前批次，已完成批次不受影响
      // - 使用 AtomicInteger 跨批次累计统计（Lambda 内需要原子操作）
      // ────────────────────────────────────────────────────────────────────────────
      AtomicInteger updatedCount = new AtomicInteger(0);
      AtomicInteger createdCount = new AtomicInteger(0);
      AtomicInteger skippedCount = new AtomicInteger(0);

      List<List<PubmedSerialData>> batches = ListUtil.partition(allRecords, BATCH_SIZE);
      int batchIndex = 0;
      for (List<PubmedSerialData> batch : batches) {
        int currentBatch = ++batchIndex;
        // TransactionTemplate.executeWithoutResult() 确保每个批次独立事务
        transactionTemplate.executeWithoutResult(
            status -> {
              log.debug("处理批次 {}/{}，记录数：{}", currentBatch, batches.size(), batch.size());
              processBatch(batch, updatedCount, createdCount, skippedCount);
            });
      }

      // ────────────────────────────────────────────────────────────────────────────
      // 阶段 4：汇总结果
      // ────────────────────────────────────────────────────────────────────────────
      long duration = timer.interval();
      log.info(
          "PubMed Venue 富化完成：解析 {} 条，更新 {} 条，新建 {} 条，跳过 {} 条，耗时 {} ms",
          allRecords.size(),
          updatedCount.get(),
          createdCount.get(),
          skippedCount.get(),
          duration);

      return VenuePubmedEnrichResult.success(
          allRecords.size(),
          updatedCount.get(),
          createdCount.get(),
          skippedCount.get(),
          command.serfileVersion(),
          command.url(),
          duration);

    } catch (ApplicationException e) {
      // 应用层异常直接抛出，保留原始错误码
      throw e;
    } catch (RuntimeException e) {
      // 运行时异常（如网络超时、解析错误）包装为统一错误码
      log.error("PubMed Venue 富化失败：{}", command.url(), e);
      throw new ApplicationException(
          CatalogErrorCode.CAT_1003, "PubMed Venue 富化失败: " + e.getMessage(), e);
    } catch (Exception e) {
      // 受检异常（如 IOException）包装为统一错误码
      log.error("PubMed Venue 富化时发生意外错误：{}", command.url(), e);
      throw new ApplicationException(
          CatalogErrorCode.CAT_1003, "PubMed Venue 富化时发生意外错误: " + e.getMessage(), e);
    }
    // 无需 finally 清理临时文件，try-with-resources 自动关闭 HTTP 连接
  }

  /// 处理一个批次的记录。
  ///
  /// 流程：收集标识符 → 批量查询 → 匹配分类 → 持久化聚合根 → 持久化子实体
  ///
  /// @param batch 待处理的记录批次
  /// @param updatedCount 更新计数器
  /// @param createdCount 新建计数器
  /// @param skippedCount 跳过计数器
  private void processBatch(
      List<PubmedSerialData> batch,
      AtomicInteger updatedCount,
      AtomicInteger createdCount,
      AtomicInteger skippedCount) {

    // 1. 收集所有标识符
    IdentifierSets identifiers = collectIdentifiers(batch);

    // 2. 批量查询现有记录
    ExistingVenuesLookup existingVenues =
        new ExistingVenuesLookup(
            venueRepository.findByIssnLs(identifiers.issnLs()),
            venueRepository.findByNlmIds(identifiers.nlmIds()),
            venueRepository.findByIssns(identifiers.issns()));

    // 3. 匹配并分类
    BatchProcessingResult result = matchAndClassifyRecords(batch, existingVenues);

    // 4. 批量持久化聚合根
    persistAggregates(result);

    // 5. 批量持久化子实体
    persistChildEntities(result);

    // 6. 更新计数器
    updatedCount.addAndGet(result.getToUpdate().size());
    createdCount.addAndGet(result.getToCreate().size());
    skippedCount.addAndGet(result.getSkippedCount());
  }

  /// 从批次中收集所有标识符。
  ///
  /// @param batch 待处理的记录批次
  /// @return 标识符集合（ISSN-L、NLM ID、ISSN）
  private IdentifierSets collectIdentifiers(List<PubmedSerialData> batch) {
    Set<String> issnLs = new HashSet<>();
    Set<String> nlmIds = new HashSet<>();
    Set<String> issns = new HashSet<>();

    for (PubmedSerialData record : batch) {
      if (record.hasIssnL()) {
        issnLs.add(record.issnL());
      }
      if (record.hasNlmId()) {
        nlmIds.add(record.nlmUniqueId());
      }
      if (record.issnPrint() != null && !record.issnPrint().isBlank()) {
        issns.add(record.issnPrint());
      }
      if (record.issnElectronic() != null && !record.issnElectronic().isBlank()) {
        issns.add(record.issnElectronic());
      }
    }
    return new IdentifierSets(issnLs, nlmIds, issns);
  }

  /// 匹配并分类记录。
  ///
  /// 根据标识符匹配现有期刊，将记录分为更新和新建两类。
  ///
  /// @param batch 待处理的记录批次
  /// @param existingVenues 现有期刊的查询结果
  /// @return 批次处理结果（包含分类和子实体数据）
  private BatchProcessingResult matchAndClassifyRecords(
      List<PubmedSerialData> batch, ExistingVenuesLookup existingVenues) {

    BatchProcessingResult result = new BatchProcessingResult();
    // 防重集合：避免同一期刊被多条 PubMed 记录更新（例如同一期刊有多个 ISSN 条目）
    Set<Long> processedVenueIds = new HashSet<>();

    for (PubmedSerialData record : batch) {
      // 按优先级匹配：ISSN-L → NLM ID → ISSN（详见 findMatch 方法）
      Optional<VenueAggregate> matched = findMatch(record, existingVenues);

      if (matched.isPresent()) {
        // ═══════════════════════════════════════════════════════════════════════
        // 情况 A：匹配到已有期刊 → 富化更新
        // ═══════════════════════════════════════════════════════════════════════
        VenueAggregate venue = matched.get();
        if (!processedVenueIds.contains(venue.getId().value())) {
          // 首次处理该期刊：更新标识符（CQRS 最小聚合）
          updateVenueIdentifiers(venue, record);
          // 构建并附加 PublicationProfile（嵌入式值对象）
          PublicationProfile profile = buildPublicationProfileFromRecord(record);
          venue.withPublicationProfile(profile);
          result.addUpdate(venue);
          processedVenueIds.add(venue.getId().value());
          // 收集 Serfile 子实体（MeSH、关系、索引历史），稍后批量持久化
          result.collectSerfileEntities(
              venue,
              toVenueMeshList(record.meshHeadings()),
              toVenueRelationList(record.titleRelations()),
              toVenueIndexingHistoryList(record.indexingHistories()));
        } else {
          // 重复记录：同一期刊已被处理，跳过（可能是 ISSN 重复匹配）
          result.markSkipped();
        }
      } else {
        // ═══════════════════════════════════════════════════════════════════════
        // 情况 B：无匹配 → 创建新期刊（PubMed 独有数据）
        // ═══════════════════════════════════════════════════════════════════════
        VenueAggregate newVenue = createVenueFromRecord(record);
        // 构建并附加 PublicationProfile（嵌入式值对象）
        PublicationProfile profile = buildPublicationProfileFromRecord(record);
        newVenue.withPublicationProfile(profile);
        result.addCreate(newVenue);
        // 收集 Serfile 子实体（MeSH、关系、索引历史），稍后批量持久化
        result.collectSerfileEntities(
            newVenue,
            toVenueMeshList(record.meshHeadings()),
            toVenueRelationList(record.titleRelations()),
            toVenueIndexingHistoryList(record.indexingHistories()));
      }
    }
    return result;
  }

  /// 批量持久化聚合根。
  ///
  /// @param result 批次处理结果
  private void persistAggregates(BatchProcessingResult result) {
    if (!result.getToUpdate().isEmpty()) {
      venueRepository.updateBatch(result.getToUpdate());
      log.debug("批量更新 {} 条记录", result.getToUpdate().size());
    }
    if (!result.getToCreate().isEmpty()) {
      venueRepository.insertAll(result.getToCreate());
      log.debug("批量创建 {} 条记录", result.getToCreate().size());
    }
  }

  /// 批量持久化 Serfile 子实体。
  ///
  /// 将 `Map<VenueAggregate, List<?>>` 转换为 `Map<Long, List<?>>` 并保存。
  ///
  /// **嵌入式值对象设计**：`PublicationProfile` 已嵌入聚合根，随聚合根一起持久化。
  /// 此方法仅处理独立存储的 Serfile 子实体（MeSH、关系、索引历史）。
  ///
  /// @param result 批次处理结果（包含聚合根已回填 ID）
  private void persistChildEntities(BatchProcessingResult result) {
    // ────────────────────────────────────────────────────────────────────────────
    // 为什么需要 Aggregate → VenueId 的转换？
    // 1. persistAggregates() 已执行 insertAll()，新建的聚合根此时 ID 已回填
    // 2. 子实体表需要 venue_id 外键，必须用 Long 类型的 ID
    // 3. 使用 toVenueIdMap() 完成转换
    // ────────────────────────────────────────────────────────────────────────────

    // 保存 Serfile 相关子实体（MeSH、关系、索引历史）
    Map<Long, List<VenueMesh>> meshByVenueId = toVenueIdMap(result.getMeshByAggregate());
    Map<Long, List<VenueRelation>> relationsByVenueId =
        toVenueIdMap(result.getRelationsByAggregate());
    Map<Long, List<VenueIndexingHistory>> historiesByVenueId =
        toVenueIdMap(result.getHistoriesByAggregate());

    // replaceSerfileDataBatch：先删除旧的 Serfile 数据，再插入新数据（全量覆盖策略）
    venueRepository.replaceSerfileDataBatch(meshByVenueId, relationsByVenueId, historiesByVenueId);

    log.debug(
        "批量保存 Serfile 子实体：MeSH {} 组，关系 {} 组，索引历史 {} 组",
        meshByVenueId.size(),
        relationsByVenueId.size(),
        historiesByVenueId.size());
  }

  /// 将聚合根映射转换为 venueId 映射（列表值版本）。
  ///
  /// 通用方法消除多处重复代码。
  ///
  /// @param aggregateMap 以聚合根为键的映射
  /// @return 以 venueId 为键的映射
  private <T> Map<Long, List<T>> toVenueIdMap(Map<VenueAggregate, List<T>> aggregateMap) {
    Map<Long, List<T>> result = new HashMap<>();
    for (Map.Entry<VenueAggregate, List<T>> entry : aggregateMap.entrySet()) {
      Long venueId = entry.getKey().getId() != null ? entry.getKey().getId().value() : null;
      if (venueId != null && !entry.getValue().isEmpty()) {
        result.put(venueId, entry.getValue());
      }
    }
    return result;
  }

  /// 按优先级匹配期刊。
  ///
  /// 优先级：ISSN-L → NLM ID → ISSN
  ///
  /// @param record 待匹配的 PubMed 记录
  /// @param lookup 现有期刊查询结果
  /// @return 匹配到的期刊聚合根
  private Optional<VenueAggregate> findMatch(PubmedSerialData record, ExistingVenuesLookup lookup) {
    // ────────────────────────────────────────────────────────────────────────────
    // 匹配优先级设计说明：
    // - ISSN-L (Linking ISSN)：国际标准，一个期刊只有一个 ISSN-L，唯一性最高
    // - NLM ID：NLM 内部标识符，在 PubMed 体系内唯一
    // - ISSN (Print/Electronic)：同一期刊可能有多个 ISSN，唯一性较低
    //
    // 优先使用唯一性高的标识符，可减少误匹配风险
    // ────────────────────────────────────────────────────────────────────────────

    // 优先级 1: ISSN-L（唯一性最高）
    if (record.hasIssnL() && lookup.byIssnL().containsKey(record.issnL())) {
      return Optional.of(lookup.byIssnL().get(record.issnL()));
    }

    // 优先级 2: NLM ID（PubMed 体系内唯一）
    if (record.hasNlmId() && lookup.byNlmId().containsKey(record.nlmUniqueId())) {
      return Optional.of(lookup.byNlmId().get(record.nlmUniqueId()));
    }

    // 优先级 3: ISSN（降级匹配，Print 优先于 Electronic）
    if (record.issnPrint() != null && lookup.byIssn().containsKey(record.issnPrint())) {
      return Optional.of(lookup.byIssn().get(record.issnPrint()));
    }

    if (record.issnElectronic() != null && lookup.byIssn().containsKey(record.issnElectronic())) {
      return Optional.of(lookup.byIssn().get(record.issnElectronic()));
    }

    // 无匹配：该记录将创建新的 VenueAggregate
    return Optional.empty();
  }

  /// 从 PubmedSerialData 更新 VenueAggregate 的标识符。
  ///
  /// **嵌入式值对象设计**：聚合根包含嵌入式值对象（PublicationProfile），一起持久化。
  ///
  /// @param venue 待更新的聚合根
  /// @param record PubMed 数据记录
  private void updateVenueIdentifiers(VenueAggregate venue, PubmedSerialData record) {
    // 添加 NLM ID 标识符（如果没有）
    if (record.hasNlmId() && venue.getIdentifier(VenueIdentifierType.NLM).isEmpty()) {
      venue.addIdentifier(VenueIdentifier.forNlm(record.nlmUniqueId()));
    }

    // 添加 ISSN-L 标识符（如果没有）
    if (record.hasIssnL() && venue.getIdentifier(VenueIdentifierType.ISSN_L).isEmpty()) {
      venue.addIdentifier(VenueIdentifier.forIssnL(record.issnL()));
    }

    // 添加 CODEN 标识符（如果有）
    if (record.coden() != null && !record.coden().isBlank()) {
      if (venue.getIdentifier(VenueIdentifierType.CODEN).isEmpty()) {
        venue.addIdentifier(VenueIdentifierType.CODEN, record.coden());
      }
    }
  }

  /// 从 PubmedSerialData 构建 PublicationProfile。
  ///
  /// 封装所有 PubMed 提供的出版相关字段，作为嵌入式值对象存储在聚合根中。
  ///
  /// @param record PubMed 数据记录
  /// @return PublicationProfile 嵌入式值对象
  private PublicationProfile buildPublicationProfileFromRecord(PubmedSerialData record) {
    // 构建语言信息
    VenueLanguages languages =
        !record.languages().isEmpty() ? toVenueLanguages(record.languages()) : null;

    // 构建出版历史
    PublicationHistory publicationHistory = null;
    if (record.publicationFirstYear() != null || record.publicationEndYear() != null) {
      publicationHistory =
          PublicationHistory.of(
              record.publicationFirstYear(), record.publicationEndYear(), record.isCeased());
    }

    // 构建索引信息（PubMed Serfile 没有 ISO Abbreviation 字段，置为 null）
    IndexingInfo indexingInfo =
        IndexingInfo.of(record.isCurrentlyIndexed() ? "MEDLINE" : null, record.medlineTA(), null);

    return PublicationProfile.builder()
        .abbreviatedTitle(record.medlineTA())
        .frequency(record.frequency())
        .publicationHistory(publicationHistory)
        .languages(languages)
        .countryCode(record.country())
        .indexingInfo(indexingInfo)
        .build();
  }

  /// 从 PubmedSerialData 创建新 VenueAggregate。
  ///
  /// **嵌入式值对象设计**：聚合根包含核心字段和嵌入式值对象（PublicationProfile 等）。
  ///
  /// @param record PubMed 数据记录
  /// @return 新创建的聚合根（只含标识符和来源信息，profile 在调用处附加）
  private VenueAggregate createVenueFromRecord(PubmedSerialData record) {
    VenueAggregate venue =
        VenueAggregate.fromPubMed(record.title(), record.nlmUniqueId(), record.issnL());

    // 添加 CODEN 标识符（如果有）
    if (record.coden() != null && !record.coden().isBlank()) {
      venue.addIdentifier(VenueIdentifierType.CODEN, record.coden());
    }

    // 其他字段（frequency, country, languages 等）通过 PublicationProfile 嵌入式值对象保存
    return venue;
  }

  /// 转换语言列表为 VenueLanguages。
  ///
  /// 根据 isPrimary 将语言分类为主语言（Primary）和摘要语言（Summary）。
  private VenueLanguages toVenueLanguages(List<PubmedLanguage> languages) {
    if (languages == null || languages.isEmpty()) {
      return VenueLanguages.empty();
    }
    List<String> primary =
        languages.stream().filter(PubmedLanguage::isPrimary).map(PubmedLanguage::code).toList();
    List<String> summary =
        languages.stream().filter(l -> !l.isPrimary()).map(PubmedLanguage::code).toList();
    return VenueLanguages.of(primary, summary);
  }

  /// 转换 MeSH 主题词列表。
  private List<VenueMesh> toVenueMeshList(List<PubmedMeshHeading> headings) {
    if (headings == null || headings.isEmpty()) {
      return List.of();
    }
    return headings.stream()
        .map(
            h -> {
              if (h.hasQualifier()) {
                return VenueMesh.create(
                    h.descriptorName(), null, h.isMajorTopic(), h.qualifierName());
              } else {
                return VenueMesh.create(h.descriptorName(), null, h.isMajorTopic());
              }
            })
        .toList();
  }

  /// 转换期刊关联关系列表。
  private List<VenueRelation> toVenueRelationList(List<PubmedTitleRelation> relations) {
    if (relations == null || relations.isEmpty()) {
      return List.of();
    }
    return relations.stream()
        .filter(PubmedTitleRelation::hasValidTitle)
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
      List<PubmedIndexingHistory> histories) {
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

  // ═══════════════════════════════════════════════════════════════════════════════
  // 内部辅助类型
  // ═══════════════════════════════════════════════════════════════════════════════

  /// 标识符集合。
  ///
  /// 封装从批次中收集的所有期刊标识符。
  private record IdentifierSets(Set<String> issnLs, Set<String> nlmIds, Set<String> issns) {}

  /// 现有期刊查询结果。
  ///
  /// 封装按不同标识符查询的结果，简化 findMatch 方法签名。
  private record ExistingVenuesLookup(
      Map<String, VenueAggregate> byIssnL,
      Map<String, VenueAggregate> byNlmId,
      Map<String, VenueAggregate> byIssn) {}

  /// 批次处理结果。
  ///
  /// 封装单批次处理的所有输出数据，避免多参数传递。
  ///
  /// **嵌入式值对象设计**：`PublicationProfile` 已嵌入聚合根，随聚合根一起持久化。
  /// 此类仅收集独立存储的 Serfile 子实体（MeSH、关系、索引历史）。
  @Getter
  private static class BatchProcessingResult {
    private final List<VenueAggregate> toUpdate = new ArrayList<>();
    private final List<VenueAggregate> toCreate = new ArrayList<>();
    private final Map<VenueAggregate, List<VenueMesh>> meshByAggregate = new HashMap<>();
    private final Map<VenueAggregate, List<VenueRelation>> relationsByAggregate = new HashMap<>();
    private final Map<VenueAggregate, List<VenueIndexingHistory>> historiesByAggregate =
        new HashMap<>();
    private int skippedCount = 0;

    void markSkipped() {
      skippedCount++;
    }

    void addUpdate(VenueAggregate venue) {
      toUpdate.add(venue);
    }

    void addCreate(VenueAggregate venue) {
      toCreate.add(venue);
    }

    /// 收集 Serfile 子实体数据。
    ///
    /// 将 MeSH、关联关系、索引历史等 Serfile 子实体关联到聚合根，稍后批量持久化。
    ///
    /// @param venue 聚合根
    /// @param meshList MeSH 主题词列表
    /// @param relationList 期刊关联关系列表
    /// @param historyList 索引历史列表
    void collectSerfileEntities(
        VenueAggregate venue,
        List<VenueMesh> meshList,
        List<VenueRelation> relationList,
        List<VenueIndexingHistory> historyList) {
      meshByAggregate.put(venue, meshList);
      relationsByAggregate.put(venue, relationList);
      historiesByAggregate.put(venue, historyList);
    }
  }
}
