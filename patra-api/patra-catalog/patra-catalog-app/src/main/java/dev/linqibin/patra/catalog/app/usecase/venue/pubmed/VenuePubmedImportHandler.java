package dev.linqibin.patra.catalog.app.usecase.venue.pubmed;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.TimeInterval;
import dev.linqibin.commons.cqrs.CommandHandler;
import dev.linqibin.commons.error.ApplicationException;
import dev.linqibin.commons.error.DomainException;
import dev.linqibin.commons.error.trait.StandardErrorTrait;
import dev.linqibin.patra.catalog.api.error.CatalogErrorCode;
import dev.linqibin.patra.catalog.app.usecase.venue.pubmed.command.VenuePubmedImportCommand;
import dev.linqibin.patra.catalog.app.usecase.venue.pubmed.dto.VenuePubmedImportResult;
import dev.linqibin.patra.catalog.domain.exception.FileDownloadException;
import dev.linqibin.patra.catalog.domain.model.aggregate.VenueAggregate;
import dev.linqibin.patra.catalog.domain.model.enums.CitationSubset;
import dev.linqibin.patra.catalog.domain.model.enums.DictionaryType;
import dev.linqibin.patra.catalog.domain.model.enums.IndexingTreatment;
import dev.linqibin.patra.catalog.domain.model.enums.VenueIdentifierType;
import dev.linqibin.patra.catalog.domain.model.enums.VenueRelationType;
import dev.linqibin.patra.catalog.domain.model.vo.common.SourceStandard;
import dev.linqibin.patra.catalog.domain.model.vo.venue.IndexingInfo;
import dev.linqibin.patra.catalog.domain.model.vo.venue.PublicationHistory;
import dev.linqibin.patra.catalog.domain.model.vo.venue.PublicationProfile;
import dev.linqibin.patra.catalog.domain.model.vo.venue.VenueIdentifier;
import dev.linqibin.patra.catalog.domain.model.vo.venue.VenueIndexingHistory;
import dev.linqibin.patra.catalog.domain.model.vo.venue.VenueLanguages;
import dev.linqibin.patra.catalog.domain.model.vo.venue.VenueMesh;
import dev.linqibin.patra.catalog.domain.model.vo.venue.VenueOpenAlexEnrichment;
import dev.linqibin.patra.catalog.domain.model.vo.venue.VenuePublicationStats;
import dev.linqibin.patra.catalog.domain.model.vo.venue.VenueRelation;
import dev.linqibin.patra.catalog.domain.model.vo.venue.pubmed.PubmedIndexingHistory;
import dev.linqibin.patra.catalog.domain.model.vo.venue.pubmed.PubmedLanguage;
import dev.linqibin.patra.catalog.domain.model.vo.venue.pubmed.PubmedMeshHeading;
import dev.linqibin.patra.catalog.domain.model.vo.venue.pubmed.PubmedSerialData;
import dev.linqibin.patra.catalog.domain.model.vo.venue.pubmed.PubmedTitleRelation;
import dev.linqibin.patra.catalog.domain.port.enrichment.OpenAlexEnrichmentQueryPort;
import dev.linqibin.patra.catalog.domain.port.parser.LsiouParserPort;
import dev.linqibin.patra.catalog.domain.port.registry.DictionaryResolverPort;
import dev.linqibin.patra.catalog.domain.port.repository.MeshDescriptorRepository;
import dev.linqibin.patra.catalog.domain.port.repository.MeshQualifierRepository;
import dev.linqibin.patra.catalog.domain.port.repository.VenueRepository;
import dev.linqibin.patra.catalog.domain.port.source.FileDownloadPort;
import dev.linqibin.patra.catalog.domain.port.source.FileDownloadResult;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
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

/// PubMed Venue 数据导入编排器。
///
/// **职责**：
///
/// - 编排 PubMed 数据导入流程（基于 NLM LSIOU）
/// - 管理事务边界（批量操作）
/// - 委派具体任务给领域端口
///
/// **导入策略**：
///
/// 增量覆盖模式：
///
/// 1. 匹配已有期刊记录时，PubMed 数据覆盖旧数据
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
public class VenuePubmedImportHandler
    implements CommandHandler<VenuePubmedImportCommand, VenuePubmedImportResult> {

  /// 批处理大小
  private static final int BATCH_SIZE = 500;
  private static final String NLM_FTP_HOST = "ftp.nlm.nih.gov";
  private static final String LSIOU_PATH_PREFIX = "/online/journals/";
  private static final String LSIOU_ARCHIVE_PREFIX = "/online/journals/archive/";

  private final FileDownloadPort fileDownloadPort;
  private final LsiouParserPort parserPort;
  private final VenueRepository venueRepository;
  private final TransactionTemplate transactionTemplate;
  private final DictionaryResolverPort dictionaryResolverPort;
  private final MeshDescriptorRepository meshDescriptorRepository;
  private final MeshQualifierRepository meshQualifierRepository;
  private final OpenAlexEnrichmentQueryPort openAlexEnrichmentQueryPort;

  /// 导入上下文：封装批次级别的查询映射结果。
  ///
  /// 用于减少方法签名中的参数数量，提升代码可读性。
  ///
  /// @param countryCodeMap 国家编码映射（原始值 → ISO 3166-1 alpha-2）
  /// @param languageCodeMap 语言代码映射（ISO 639-3 → BCP 47）
  /// @param descriptorUiMap MeSH 描述符名称 → UI 映射
  /// @param qualifierUiMap MeSH 限定词名称 → UI 映射
  /// @param openAlexEnrichmentMap ISSN-L → OpenAlex 富化数据映射（引用指标 + 年度统计）
  private record ImportContext(
      Map<String, String> countryCodeMap,
      Map<String, String> languageCodeMap,
      Map<String, String> descriptorUiMap,
      Map<String, String> qualifierUiMap,
      Map<String, VenueOpenAlexEnrichment> openAlexEnrichmentMap) {}

  /// 执行 PubMed Venue 数据导入。
  ///
  /// **事务策略**：批次级别事务，每 500 条记录独立提交。
  /// 失败时仅回滚当前批次，已完成批次不受影响。
  ///
  /// **下载策略**：
  ///
  /// - 下载到临时文件，解耦 HTTP 连接与数据处理
  /// - 使用 try-with-resources 自动清理临时文件
  ///
  /// @param command 导入命令（包含 URL 和版本号）
  /// @return 导入结果摘要
  @Override
  public VenuePubmedImportResult handle(VenuePubmedImportCommand command) {
    TimeInterval timer = DateUtil.timer();
    log.info("启动 PubMed Venue 导入，URL：{}，版本：{}", command.url(), command.lsiouVersion());

    // ────────────────────────────────────────────────────────────────────────────
    // 阶段 1：下载到临时文件
    // - 使用 try-with-resources 管理临时文件生命周期
    // - 下载到本地临时文件，解耦 HTTP 连接与解析处理
    // ────────────────────────────────────────────────────────────────────────────
    try (DownloadContext context = openDownloadContext(command.url())) {
      FileDownloadResult downloadResult = context.result();
      String sourceUrl = context.sourceUrl();

      log.info("文件下载完成（{} 字节），开始解析，实际来源：{}", downloadResult.fileSize(), sourceUrl);

      // ────────────────────────────────────────────────────────────────────────────
      // 阶段 2：XML 解析
      // - 将 NLM LSIOU XML 解析为 PubmedSerialData 领域对象
      // - 一次性加载所有记录到内存（LSIOU 约 1.5 万条，可承受）
      // ────────────────────────────────────────────────────────────────────────────
      List<PubmedSerialData> allRecords;
      try (InputStream fileStream = Files.newInputStream(downloadResult.filePath())) {
        allRecords = parserPort.parse(fileStream).toList();
      }
      log.info("解析完成，记录数：{}", allRecords.size());

      if (allRecords.isEmpty()) {
        return VenuePubmedImportResult.success(
            0, 0, 0, 0, command.lsiouVersion(), sourceUrl, timer.interval());
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
          "PubMed Venue 导入完成：解析 {} 条，更新 {} 条，新建 {} 条，跳过 {} 条，耗时 {} ms",
          allRecords.size(),
          updatedCount.get(),
          createdCount.get(),
          skippedCount.get(),
          duration);

      return VenuePubmedImportResult.success(
          allRecords.size(),
          updatedCount.get(),
          createdCount.get(),
          skippedCount.get(),
          command.lsiouVersion(),
          sourceUrl,
          duration);

    } catch (DomainException | ApplicationException e) {
      // 领域异常直接传播（保留语义特征），应用层异常保留原始错误码
      throw e;
    } catch (RuntimeException e) {
      // 运行时异常（如网络超时、解析错误）包装为统一错误码
      log.error("PubMed Venue 导入失败：{}", command.url(), e);
      throw new ApplicationException(
          CatalogErrorCode.CAT_1301, "PubMed Venue 导入失败: " + e.getMessage(), e);
    } catch (Exception e) {
      // 受检异常（如 IOException）包装为统一错误码
      log.error("PubMed Venue 导入时发生意外错误：{}", command.url(), e);
      throw new ApplicationException(
          CatalogErrorCode.CAT_1301, "PubMed Venue 导入时发生意外错误: " + e.getMessage(), e);
    }
    // try-with-resources 自动调用 DownloadContext.close() 清理临时文件
  }

  /// 下载文件到临时目录，若主目录不存在则回退到 archive 目录。
  ///
  /// @param urlString 下载 URL
  /// @return 下载上下文（包含临时文件和实际来源 URL）
  private DownloadContext openDownloadContext(String urlString) {
    URI primaryUrl = URI.create(urlString);
    try {
      return new DownloadContext(fileDownloadPort.download(primaryUrl), primaryUrl.toString());
    } catch (FileDownloadException ex) {
      if (shouldTryArchive(primaryUrl, ex)) {
        URI archiveUrl = toArchiveUrl(primaryUrl);
        log.warn("LSIOU 主目录文件不存在，尝试 archive 目录：{}", archiveUrl);
        return new DownloadContext(fileDownloadPort.download(archiveUrl), archiveUrl.toString());
      }
      throw ex;
    }
  }

  /// 判断是否需要回退到 archive 目录。
  ///
  /// @param url 原始下载 URL
  /// @param exception 下载异常
  /// @return 是否需要回退
  private boolean shouldTryArchive(URI url, FileDownloadException exception) {
    if (url == null || exception == null) {
      return false;
    }
    if (!isNlmFtpUrl(url) || isArchivePath(url)) {
      return false;
    }
    return exception.getErrorTraits().contains(StandardErrorTrait.NOT_FOUND);
  }

  /// 构建 archive 目录的 URL。
  ///
  /// @param url 原始下载 URL
  /// @return archive 目录 URL
  private URI toArchiveUrl(URI url) {
    String path = url.getPath();
    if (path == null) {
      return url;
    }
    String normalizedPath = path.startsWith("/") ? path : "/" + path;
    if (!normalizedPath.startsWith(LSIOU_PATH_PREFIX)
        || normalizedPath.startsWith(LSIOU_ARCHIVE_PREFIX)) {
      return url;
    }
    String archivePath =
        LSIOU_ARCHIVE_PREFIX + normalizedPath.substring(LSIOU_PATH_PREFIX.length());
    String authority = url.getAuthority();
    StringBuilder builder = new StringBuilder();
    builder.append(url.getScheme()).append("://").append(authority).append(archivePath);
    if (url.getQuery() != null && !url.getQuery().isBlank()) {
      builder.append("?").append(url.getQuery());
    }
    if (url.getFragment() != null && !url.getFragment().isBlank()) {
      builder.append("#").append(url.getFragment());
    }
    return URI.create(builder.toString());
  }

  /// 判断是否为 NLM FTP 的 LSIOU 路径。
  ///
  /// @param url 下载 URL
  /// @return 是否为 NLM FTP LSIOU 路径
  private boolean isNlmFtpUrl(URI url) {
    if (url == null) {
      return false;
    }
    String host = url.getHost();
    String path = url.getPath();
    return "ftp".equalsIgnoreCase(url.getScheme())
        && NLM_FTP_HOST.equalsIgnoreCase(host)
        && path != null
        && path.startsWith(LSIOU_PATH_PREFIX);
  }

  /// 判断 URL 是否已指向 archive 目录。
  ///
  /// @param url 下载 URL
  /// @return 是否为 archive 目录
  private boolean isArchivePath(URI url) {
    String path = url.getPath();
    return path != null && path.startsWith(LSIOU_ARCHIVE_PREFIX);
  }

  /// 下载上下文（携带临时文件和实际来源 URL）。
  ///
  /// @param result 文件下载结果（包含临时文件路径）
  /// @param sourceUrl 实际来源 URL
  private record DownloadContext(FileDownloadResult result, String sourceUrl)
      implements AutoCloseable {

    @Override
    public void close() throws Exception {
      Files.deleteIfExists(result.filePath());
    }
  }

  /// 处理一个批次的记录。
  ///
  /// 流程：收集标识符 → 批量查询 → 解析国家编码 → 匹配分类 → 持久化聚合根 → 持久化子实体
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

    // 3. 收集并解析国家编码（每批次一次 RPC 调用）
    // PubMed LSIOU Country 字段使用英文全名（NAME_EN 标准）
    Set<String> rawCountryCodes = collectCountryCodes(batch);
    Map<String, String> countryCodeMap =
        dictionaryResolverPort.resolve(
            DictionaryType.COUNTRY, SourceStandard.NAME_EN, rawCountryCodes);

    // 3.5. 收集并解析语言代码（每批次一次 RPC 调用）
    // PubMed LSIOU Language 字段使用 ISO 639-3 三字母代码（如 eng, chi, jpn）
    // 转换为平台标准 BCP 47 格式（如 en, zh, ja）
    Set<String> rawLanguageCodes = collectLanguageCodes(batch);
    Map<String, String> languageCodeMap =
        dictionaryResolverPort.resolve(
            DictionaryType.LANGUAGE, SourceStandard.ISO_639_3, rawLanguageCodes);
    log.debug("语言代码解析完成：原始 {} 个，有效映射 {} 个", rawLanguageCodes.size(), languageCodeMap.size());

    // 3.6. 收集并查询 MeSH 主题词 UI（每批次两次数据库查询）
    Set<String> meshDescriptorNames = collectMeshDescriptorNames(batch);
    Set<String> meshQualifierNames = collectMeshQualifierNames(batch);
    Map<String, String> descriptorUiMap =
        meshDescriptorRepository.findAllByNameIn(meshDescriptorNames);
    Map<String, String> qualifierUiMap =
        meshQualifierRepository.findAllByNameIn(meshQualifierNames);
    log.debug(
        "MeSH UI 查询完成：描述符原始 {} 个/匹配 {} 个，限定词原始 {} 个/匹配 {} 个",
        meshDescriptorNames.size(),
        descriptorUiMap.size(),
        meshQualifierNames.size(),
        qualifierUiMap.size());

    // 3.7. 批量查询 OpenAlex 富化数据（引用指标 + 年度统计，通过 REST API，基于 ISSN-L）
    Map<String, VenueOpenAlexEnrichment> openAlexEnrichmentMap =
        openAlexEnrichmentQueryPort.findEnrichmentData(identifiers.issnLs());
    log.debug(
        "OpenAlex 富化查询完成：ISSN-L 共 {} 个，匹配 {} 个",
        identifiers.issnLs().size(),
        openAlexEnrichmentMap.size());

    // 4. 封装导入上下文并匹配分类
    ImportContext context =
        new ImportContext(
            countryCodeMap,
            languageCodeMap,
            descriptorUiMap,
            qualifierUiMap,
            openAlexEnrichmentMap);
    BatchProcessingResult result = matchAndClassifyRecords(batch, existingVenues, context);

    // 5. 批量持久化聚合根
    persistAggregates(result);

    // 6. 批量持久化子实体
    persistChildEntities(result);

    // 7. 更新计数器
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
      if (record.hasIssnPrint()) {
        issns.add(record.issnPrint());
      }
      if (record.hasIssnElectronic()) {
        issns.add(record.issnElectronic());
      }
    }
    return new IdentifierSets(issnLs, nlmIds, issns);
  }

  /// 从批次中收集所有国家编码。
  ///
  /// 收集所有非空的国家编码，用于批量解析成 ISO 3166-1 alpha-2 标准编码。
  ///
  /// @param batch 待处理的记录批次
  /// @return 国家编码集合
  private Set<String> collectCountryCodes(List<PubmedSerialData> batch) {
    Set<String> countryCodes = new HashSet<>();
    for (PubmedSerialData record : batch) {
      if (record.country() != null && !record.country().isBlank()) {
        countryCodes.add(record.country());
      }
    }
    return countryCodes;
  }

  /// 从批次中收集所有语言代码。
  ///
  /// 收集所有非空的语言代码（ISO 639-3 格式），用于批量解析成 BCP 47 标准编码。
  ///
  /// @param batch 待处理的记录批次
  /// @return 语言代码集合
  private Set<String> collectLanguageCodes(List<PubmedSerialData> batch) {
    Set<String> languageCodes = new HashSet<>();
    for (PubmedSerialData record : batch) {
      for (PubmedLanguage language : record.languages()) {
        if (language.code() != null && !language.code().isBlank()) {
          languageCodes.add(language.code());
        }
      }
    }
    return languageCodes;
  }

  /// 从批次中收集所有 MeSH 描述符名称。
  ///
  /// 收集所有非空的 MeSH 描述符名称，用于批量查询 UI。
  ///
  /// @param batch 待处理的记录批次
  /// @return MeSH 描述符名称集合
  private Set<String> collectMeshDescriptorNames(List<PubmedSerialData> batch) {
    Set<String> descriptorNames = new HashSet<>();
    for (PubmedSerialData record : batch) {
      for (PubmedMeshHeading heading : record.meshHeadings()) {
        if (heading.descriptorName() != null && !heading.descriptorName().isBlank()) {
          descriptorNames.add(heading.descriptorName());
        }
      }
    }
    return descriptorNames;
  }

  /// 从批次中收集所有 MeSH 限定词名称。
  ///
  /// 收集所有非空的 MeSH 限定词名称，用于批量查询 UI。
  ///
  /// @param batch 待处理的记录批次
  /// @return MeSH 限定词名称集合
  private Set<String> collectMeshQualifierNames(List<PubmedSerialData> batch) {
    Set<String> qualifierNames = new HashSet<>();
    for (PubmedSerialData record : batch) {
      for (PubmedMeshHeading heading : record.meshHeadings()) {
        if (heading.hasQualifier()
            && heading.qualifierName() != null
            && !heading.qualifierName().isBlank()) {
          qualifierNames.add(heading.qualifierName());
        }
      }
    }
    return qualifierNames;
  }

  /// 匹配并分类记录。
  ///
  /// 根据标识符匹配现有期刊，将记录分为更新和新建两类。
  ///
  /// @param batch 待处理的记录批次
  /// @param existingVenues 现有期刊的查询结果
  /// @param context 导入上下文（包含国家/语言/MeSH 映射）
  /// @return 批次处理结果（包含分类和子实体数据）
  private BatchProcessingResult matchAndClassifyRecords(
      List<PubmedSerialData> batch, ExistingVenuesLookup existingVenues, ImportContext context) {

    BatchProcessingResult result = new BatchProcessingResult();
    // 防重集合（UPDATE 路径）：避免同一期刊被多条 PubMed 记录更新
    Set<Long> processedVenueIds = new HashSet<>();
    // 防重集合（CREATE 路径）：避免同批次内创建重复期刊
    Set<String> createdNlmIds = new HashSet<>();
    Set<String> createdIssnLs = new HashSet<>();

    for (PubmedSerialData record : batch) {
      // ═══════════════════════════════════════════════════════════════════════
      // 前置检查：跳过已从 NLM 目录删除的 Serial
      // ═══════════════════════════════════════════════════════════════════════
      if (record.deletedTimestamp() != null) {
        result.markSkipped();
        log.debug(
            "跳过已删除的 Serial：NLM ID [{}]，删除时间 [{}]", record.nlmUniqueId(), record.deletedTimestamp());
        continue;
      }

      // 按优先级匹配：ISSN-L → NLM ID → ISSN（详见 findMatch 方法）
      Optional<VenueAggregate> matched = findMatch(record, existingVenues);

      if (matched.isPresent()) {
        // ═══════════════════════════════════════════════════════════════════════
        // 情况 A：匹配到已有期刊 → 覆盖更新
        // ═══════════════════════════════════════════════════════════════════════
        VenueAggregate venue = matched.get();
        if (!processedVenueIds.contains(venue.getId().value())) {
          // 首次处理该期刊：补全标识符
          updateVenueIdentifiers(venue, record);
          // 构建并附加 PublicationProfile（嵌入式值对象）
          PublicationProfile profile =
              buildPublicationProfileFromRecord(record, context.countryCodeMap());
          venue.withPublicationProfile(profile);
          // OpenAlex 富化（引用指标 + OpenAlex ID + 年度统计收集）
          VenueOpenAlexEnrichment openAlexEnrichment = resolveOpenAlexEnrichment(record, context);
          applyOpenAlexEnrichment(venue, openAlexEnrichment);
          result.collectYearlyStats(venue, openAlexEnrichment);
          // 标准化语言代码：ISO 639-3 → BCP 47
          venue.normalizeLanguages(context.languageCodeMap());
          result.addUpdate(venue);
          processedVenueIds.add(venue.getId().value());
          // 收集 PubMed 子实体（MeSH、关系、索引历史），稍后批量持久化
          result.collectPubmedEntities(
              venue,
              toVenueMeshList(
                  record.meshHeadings(), context.descriptorUiMap(), context.qualifierUiMap()),
              toVenueRelationList(record.titleRelations()),
              toVenueIndexingHistoryList(record.indexingHistories()));
        } else {
          // 重复记录：同一期刊已被处理，跳过（可能是 ISSN 重复匹配）
          result.markSkipped();
        }
      } else {
        // ═══════════════════════════════════════════════════════════════════════
        // 情况 B：无匹配 → 创建新期刊
        // ═══════════════════════════════════════════════════════════════════════

        // 批内去重：检查当前批次是否已创建了相同 NLM ID 或 ISSN-L 的期刊
        if (isDuplicateInCurrentBatch(record, createdNlmIds, createdIssnLs)) {
          result.markSkipped();
          log.debug("跳过批内重复 Serial：NLM ID [{}]，ISSN-L [{}]", record.nlmUniqueId(), record.issnL());
          continue;
        }

        VenueAggregate newVenue = createVenueFromRecord(record);
        // 构建并附加 PublicationProfile（嵌入式值对象）
        PublicationProfile profile =
            buildPublicationProfileFromRecord(record, context.countryCodeMap());
        newVenue.withPublicationProfile(profile);
        // OpenAlex 富化（引用指标 + OpenAlex ID + 年度统计收集）
        VenueOpenAlexEnrichment openAlexEnrichment = resolveOpenAlexEnrichment(record, context);
        applyOpenAlexEnrichment(newVenue, openAlexEnrichment);
        result.collectYearlyStats(newVenue, openAlexEnrichment);
        // 标准化语言代码：ISO 639-3 → BCP 47
        newVenue.normalizeLanguages(context.languageCodeMap());
        result.addCreate(newVenue);

        // 记录已创建的标识符，供批内去重使用
        if (record.hasNlmId()) {
          createdNlmIds.add(record.nlmUniqueId());
        }
        if (record.hasIssnL()) {
          createdIssnLs.add(record.issnL());
        }

        // 收集 PubMed 子实体（MeSH、关系、索引历史），稍后批量持久化
        result.collectPubmedEntities(
            newVenue,
            toVenueMeshList(
                record.meshHeadings(), context.descriptorUiMap(), context.qualifierUiMap()),
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

  /// 批量持久化 PubMed 子实体。
  ///
  /// 将 `Map<VenueAggregate, List<?>>` 转换为 `Map<Long, List<?>>` 并保存。
  ///
  /// **嵌入式值对象设计**：`PublicationProfile` 已嵌入聚合根，随聚合根一起持久化。
  /// 此方法仅处理独立存储的 PubMed 子实体（MeSH、关系、索引历史）。
  ///
  /// @param result 批次处理结果（包含聚合根已回填 ID）
  private void persistChildEntities(BatchProcessingResult result) {
    // ────────────────────────────────────────────────────────────────────────────
    // 为什么需要 Aggregate → VenueId 的转换？
    // 1. persistAggregates() 已执行 insertAll()，新建的聚合根此时 ID 已回填
    // 2. 子实体表需要 venue_id 外键，必须用 Long 类型的 ID
    // 3. 使用 toVenueIdMap() 完成转换
    // ────────────────────────────────────────────────────────────────────────────

    // 保存 PubMed 相关子实体（MeSH、关系、索引历史）
    Map<Long, List<VenueMesh>> meshByVenueId = toVenueIdMap(result.getMeshByAggregate());
    Map<Long, List<VenueRelation>> relationsByVenueId =
        toVenueIdMap(result.getRelationsByAggregate());
    Map<Long, List<VenueIndexingHistory>> historiesByVenueId =
        toVenueIdMap(result.getHistoriesByAggregate());

    // replacePubmedDataBatch：先删除旧的 PubMed 数据，再插入新数据（全量覆盖策略）
    venueRepository.replacePubmedDataBatch(meshByVenueId, relationsByVenueId, historiesByVenueId);

    log.debug(
        "批量保存 PubMed 子实体：MeSH {} 组，关系 {} 组，索引历史 {} 组",
        meshByVenueId.size(),
        relationsByVenueId.size(),
        historiesByVenueId.size());

    // 保存 OpenAlex 年度统计
    Map<Long, List<VenuePublicationStats>> yearlyStatsByVenueId =
        toVenueIdMap(result.getYearlyStatsByAggregate());
    if (!yearlyStatsByVenueId.isEmpty()) {
      venueRepository.replaceYearlyMetricsBatch(yearlyStatsByVenueId);
      log.debug("批量保存 OpenAlex 年度统计：{} 组", yearlyStatsByVenueId.size());
    }
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
    if (record.hasIssnPrint() && lookup.byIssn().containsKey(record.issnPrint())) {
      return Optional.of(lookup.byIssn().get(record.issnPrint()));
    }

    if (record.hasIssnElectronic() && lookup.byIssn().containsKey(record.issnElectronic())) {
      return Optional.of(lookup.byIssn().get(record.issnElectronic()));
    }

    // 无匹配：该记录将创建新的 VenueAggregate
    return Optional.empty();
  }

  /// 判断记录是否与当前批次中已创建的期刊重复。
  ///
  /// 基于 NLM ID 和 ISSN-L 判断，两者都是全局唯一标识符。
  ///
  /// @param record 待检查的 PubMed 记录
  /// @param createdNlmIds 当前批次已创建的 NLM ID 集合
  /// @param createdIssnLs 当前批次已创建的 ISSN-L 集合
  /// @return 如果重复返回 true
  private boolean isDuplicateInCurrentBatch(
      PubmedSerialData record, Set<String> createdNlmIds, Set<String> createdIssnLs) {
    if (record.hasNlmId() && createdNlmIds.contains(record.nlmUniqueId())) {
      return true;
    }
    return record.hasIssnL() && createdIssnLs.contains(record.issnL());
  }

  /// 从 PubmedSerialData 补全 VenueAggregate 的标识符。
  ///
  /// 将 PubMed 记录中的所有标识符同步到聚合根，包括：
  /// NLM ID、ISSN-L、CODEN、ISSN Print、ISSN Electronic。
  /// `addIdentifier()` 内部保证幂等，已存在的标识符不会重复添加。
  ///
  /// @param venue 待更新的聚合根
  /// @param record PubMed 数据记录
  private void updateVenueIdentifiers(VenueAggregate venue, PubmedSerialData record) {
    // 添加 NLM ID 标识符
    if (record.hasNlmId()) {
      venue.addIdentifier(VenueIdentifier.forNlm(record.nlmUniqueId()));
    }

    // 添加 ISSN-L 标识符
    if (record.hasIssnL()) {
      venue.addIdentifier(VenueIdentifier.forIssnL(record.issnL()));
    }

    // 添加 CODEN 标识符
    if (record.hasCoden()) {
      venue.addIdentifier(VenueIdentifierType.CODEN, record.coden());
    }

    // 添加 ISSN Print 标识符
    if (record.hasIssnPrint()) {
      venue.addIdentifier(VenueIdentifierType.ISSN, record.issnPrint());
    }

    // 添加 ISSN Electronic 标识符
    if (record.hasIssnElectronic()) {
      venue.addIdentifier(VenueIdentifierType.ISSN, record.issnElectronic());
    }
  }

  /// 从 PubmedSerialData 构建 PublicationProfile。
  ///
  /// 封装所有 PubMed 提供的出版相关字段，作为嵌入式值对象存储在聚合根中。
  ///
  /// @param record PubMed 数据记录
  /// @param countryCodeMap 国家编码映射（原始值 → ISO 3166-1 alpha-2）
  /// @return PublicationProfile 嵌入式值对象
  private PublicationProfile buildPublicationProfileFromRecord(
      PubmedSerialData record, Map<String, String> countryCodeMap) {
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

    // 构建索引信息（PubMed LSIOU 没有 ISO Abbreviation 字段，置为 null）
    IndexingInfo indexingInfo =
        IndexingInfo.of(record.isCurrentlyIndexed() ? "MEDLINE" : null, record.medlineTA(), null);

    // 获取标准化的国家编码（通过 registry 服务解析）
    String rawCountry = record.country();
    String resolvedCountryCode =
        (rawCountry != null && !rawCountry.isBlank()) ? countryCodeMap.get(rawCountry) : null;

    return PublicationProfile.builder()
        .abbreviatedTitle(record.medlineTA())
        .frequency(record.frequency())
        .publicationHistory(publicationHistory)
        .languages(languages)
        .countryCode(resolvedCountryCode)
        .indexingInfo(indexingInfo)
        .build();
  }

  /// 从 ImportContext 中按 ISSN-L 解析 OpenAlex 富化数据。
  ///
  /// @param record PubMed 数据记录
  /// @param context 导入上下文
  /// @return 富化数据，如果无 ISSN-L 或无匹配则返回 null
  private VenueOpenAlexEnrichment resolveOpenAlexEnrichment(
      PubmedSerialData record, ImportContext context) {
    return record.hasIssnL() ? context.openAlexEnrichmentMap().get(record.issnL()) : null;
  }

  /// 将 OpenAlex 富化数据应用到聚合根。
  ///
  /// 设置引用指标快照、开放获取信息并添加 OpenAlex ID 标识符。
  ///
  /// @param venue 待富化的聚合根
  /// @param enrichment OpenAlex 富化数据（可为 null）
  private void applyOpenAlexEnrichment(VenueAggregate venue, VenueOpenAlexEnrichment enrichment) {
    if (enrichment == null) {
      return;
    }
    if (enrichment.hasCitationMetrics()) {
      venue.withCitationMetrics(enrichment.citationMetrics());
    }
    if (enrichment.hasOpenAccessInfo()) {
      venue.withOpenAccess(enrichment.openAccessInfo());
    }
    if (enrichment.openAlexId() != null && !enrichment.openAlexId().isBlank()) {
      venue.addIdentifier(VenueIdentifierType.OPENALEX, enrichment.openAlexId());
    }
  }

  /// 从 PubmedSerialData 创建新 VenueAggregate。
  ///
  /// 创建聚合根并附加所有可用的标识符（NLM ID、ISSN-L 由工厂方法添加）。
  /// 其他字段（frequency、country、languages 等）通过 PublicationProfile 嵌入式值对象保存。
  ///
  /// @param record PubMed 数据记录
  /// @return 新创建的聚合根（只含标识符和来源信息，profile 和富化在调用处附加）
  private VenueAggregate createVenueFromRecord(PubmedSerialData record) {
    VenueAggregate venue =
        VenueAggregate.fromPubMed(record.title(), record.nlmUniqueId(), record.issnL());

    // 添加 CODEN 标识符
    if (record.hasCoden()) {
      venue.addIdentifier(VenueIdentifierType.CODEN, record.coden());
    }

    // 添加 ISSN Print 标识符
    if (record.hasIssnPrint()) {
      venue.addIdentifier(VenueIdentifierType.ISSN, record.issnPrint());
    }

    // 添加 ISSN Electronic 标识符
    if (record.hasIssnElectronic()) {
      venue.addIdentifier(VenueIdentifierType.ISSN, record.issnElectronic());
    }

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
  ///
  /// @param headings PubMed MeSH 主题词列表
  /// @param descriptorUiMap 描述符名称 → UI 映射表
  /// @param qualifierUiMap 限定词名称 → UI 映射表
  /// @return VenueMesh 列表
  private List<VenueMesh> toVenueMeshList(
      List<PubmedMeshHeading> headings,
      Map<String, String> descriptorUiMap,
      Map<String, String> qualifierUiMap) {
    if (headings == null || headings.isEmpty()) {
      return List.of();
    }
    return headings.stream()
        .map(
            h -> {
              // 查询描述符 UI（匹配失败返回 null）
              String descriptorUi = descriptorUiMap.get(h.descriptorName());

              if (h.hasQualifier()) {
                // 查询限定词 UI（匹配失败返回 null）
                String qualifierUi = qualifierUiMap.get(h.qualifierName());
                return VenueMesh.create(
                    h.descriptorName(),
                    descriptorUi,
                    h.isMajorTopic(),
                    h.qualifierName(),
                    qualifierUi);
              } else {
                return VenueMesh.create(h.descriptorName(), descriptorUi, h.isMajorTopic());
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
              VenueRelationType type = VenueRelationType.fromLsiouTitleType(r.titleType());
              // 如果无法识别类型，默认使用 PRECEDING（最常见的关系类型）
              if (type == null) {
                type = VenueRelationType.PRECEDING;
                log.debug("无法识别的 TitleType：{}，默认使用 PRECEDING", r.titleType());
              }
              return VenueRelation.create(r.relatedTitle(), type, r.getNlmRecordId());
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
                    "MEDLINE", treatment, citationSubset, null, null, null, null, null, null);
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
  /// 此类仅收集独立存储的 PubMed 子实体（MeSH、关系、索引历史）。
  @Getter
  private static class BatchProcessingResult {
    private final List<VenueAggregate> toUpdate = new ArrayList<>();
    private final List<VenueAggregate> toCreate = new ArrayList<>();
    private final Map<VenueAggregate, List<VenueMesh>> meshByAggregate = new HashMap<>();
    private final Map<VenueAggregate, List<VenueRelation>> relationsByAggregate = new HashMap<>();
    private final Map<VenueAggregate, List<VenueIndexingHistory>> historiesByAggregate =
        new HashMap<>();
    private final Map<VenueAggregate, List<VenuePublicationStats>> yearlyStatsByAggregate =
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

    /// 收集 PubMed 子实体数据。
    ///
    /// 将 MeSH、关联关系、索引历史等 PubMed 子实体关联到聚合根，稍后批量持久化。
    ///
    /// @param venue 聚合根
    /// @param meshList MeSH 主题词列表
    /// @param relationList 期刊关联关系列表
    /// @param historyList 索引历史列表
    void collectPubmedEntities(
        VenueAggregate venue,
        List<VenueMesh> meshList,
        List<VenueRelation> relationList,
        List<VenueIndexingHistory> historyList) {
      meshByAggregate.put(venue, meshList);
      relationsByAggregate.put(venue, relationList);
      historiesByAggregate.put(venue, historyList);
    }

    /// 收集 OpenAlex 年度统计数据。
    ///
    /// @param venue 聚合根
    /// @param enrichment OpenAlex 富化数据（可为 null）
    void collectYearlyStats(VenueAggregate venue, VenueOpenAlexEnrichment enrichment) {
      if (enrichment != null && enrichment.hasYearlyStats()) {
        yearlyStatsByAggregate.put(venue, enrichment.yearlyStats());
      }
    }
  }
}
