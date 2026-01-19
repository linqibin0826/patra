package com.patra.catalog.infra.adapter.batch.publication;

import com.patra.catalog.domain.model.aggregate.PublicationAggregate;
import com.patra.catalog.domain.model.aggregate.VenueInstanceAggregate;
import com.patra.catalog.domain.model.enums.PublicationStatus;
import com.patra.catalog.domain.model.vo.publication.LanguageInfo;
import com.patra.catalog.domain.model.vo.publication.PublicationIdentifiers;
import com.patra.catalog.domain.model.vo.venue.VenueId;
import com.patra.catalog.domain.model.vo.venueinstance.JournalInstanceParams;
import com.patra.catalog.domain.port.gateway.VenueInstanceGateway;
import com.patra.catalog.domain.port.repository.PublicationRepository;
import com.patra.catalog.infra.adapter.batch.publication.cache.VenueCache;
import com.patra.common.enums.ProvenanceCode;
import com.patra.common.model.CanonicalPublication;
import com.patra.common.model.CanonicalPublication.Identifier;
import com.patra.common.model.CanonicalPublication.Journal;
import com.patra.common.model.CanonicalPublication.PublicationDates;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ItemProcessor;

/// PubMed 文献处理器。
///
/// **职责**：
///
/// 1. 去重：检查 PMID 是否已存在，跳过重复记录
/// 2. Venue 匹配：通过 NLM ID/ISSN 匹配载体
/// 3. VenueInstance 创建：获取或创建载体实例（卷期）
/// 4. 聚合根构建：将 CanonicalPublication 转换为 PublicationAggregate
///
/// **跳过条件**：
///
/// - PMID 已存在（去重）
/// - 无法匹配 Venue（数据不完整）
///
/// **返回 null 表示跳过该记录**，Spring Batch 会自动忽略。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@RequiredArgsConstructor
public class PubmedArticleItemProcessor
    implements ItemProcessor<CanonicalPublication, PublicationAggregate> {

  private final PublicationRepository publicationRepository;
  private final VenueCache venueCache;
  private final VenueInstanceGateway venueInstanceGateway;

  @Override
  public PublicationAggregate process(CanonicalPublication publication) throws Exception {
    String pmid = extractPmid(publication);

    if (pmid == null) {
      log.debug("跳过缺少 PMID 的文献");
      return null;
    }

    // 1. 去重检查
    if (publicationRepository.existsByPmid(pmid)) {
      log.debug("跳过已存在的文献：PMID={}", pmid);
      return null;
    }

    // 2. Venue 匹配
    Journal journal = publication.getJournal();
    if (journal == null) {
      log.debug("跳过缺少期刊信息的文献：PMID={}", pmid);
      return null;
    }

    String nlmUniqueId = journal.getNlmUniqueId();
    List<String> issns = collectIssns(journal);
    Optional<VenueId> venueIdOpt = venueCache.findByPriority(nlmUniqueId, issns);

    if (venueIdOpt.isEmpty()) {
      log.debug("无法匹配 Venue，跳过文献：PMID={}, NLM={}, ISSNs={}", pmid, nlmUniqueId, issns);
      return null;
    }

    VenueId venueId = venueIdOpt.get();

    // 3. VenueInstance 创建/获取
    Integer pubYear = extractPublicationYear(publication);
    JournalInstanceParams params =
        JournalInstanceParams.builder()
            .venueId(venueId)
            .volume(journal.getVolume())
            .issue(journal.getIssue())
            .publicationYear(pubYear)
            .publicationMonth(extractPublicationMonth(publication))
            .publicationDay(extractPublicationDay(publication))
            .build();
    VenueInstanceAggregate venueInstance = venueInstanceGateway.findOrCreateJournalInstance(params);

    // 4. 构建 PublicationAggregate
    return buildPublicationAggregate(publication, pmid, venueId, venueInstance, pubYear);
  }

  /// 从 CanonicalPublication 提取 PMID。
  private String extractPmid(CanonicalPublication publication) {
    if (publication.getIdentifiers() == null) {
      return null;
    }
    return publication.getIdentifiers().stream()
        .filter(id -> "pmid".equals(id.getType()))
        .map(Identifier::getValue)
        .findFirst()
        .orElse(null);
  }

  /// 从 CanonicalPublication 提取 DOI。
  private String extractDoi(CanonicalPublication publication) {
    if (publication.getIdentifiers() == null) {
      return null;
    }
    return publication.getIdentifiers().stream()
        .filter(id -> "doi".equals(id.getType()))
        .map(Identifier::getValue)
        .findFirst()
        .orElse(null);
  }

  /// 从 CanonicalPublication 提取出版年份。
  private Integer extractPublicationYear(CanonicalPublication publication) {
    PublicationDates dates = publication.getDates();
    if (dates == null || dates.getPublished() == null) {
      return null;
    }
    return dates.getPublished().getYear();
  }

  /// 从 CanonicalPublication 提取出版月份。
  private Integer extractPublicationMonth(CanonicalPublication publication) {
    PublicationDates dates = publication.getDates();
    if (dates == null || dates.getPublished() == null) {
      return null;
    }
    return dates.getPublished().getMonthValue();
  }

  /// 从 CanonicalPublication 提取出版日期。
  private Integer extractPublicationDay(CanonicalPublication publication) {
    PublicationDates dates = publication.getDates();
    if (dates == null || dates.getPublished() == null) {
      return null;
    }
    return dates.getPublished().getDayOfMonth();
  }

  /// 收集所有 ISSN（按优先级排序：Linking → Print/Electronic）。
  ///
  /// Venue 匹配优先级：nlmUniqueId > issnLinking > issn
  private List<String> collectIssns(Journal journal) {
    List<String> issns = new ArrayList<>(2);

    // ISSN-L 优先级最高
    if (journal.getIssnLinking() != null && !journal.getIssnLinking().isBlank()) {
      issns.add(journal.getIssnLinking());
    }

    // 主 ISSN（已在解析阶段确定为 Print 或 Electronic）
    if (journal.getIssn() != null && !journal.getIssn().isBlank()) {
      issns.add(journal.getIssn());
    }

    return issns;
  }

  /// 构建 PublicationAggregate。
  private PublicationAggregate buildPublicationAggregate(
      CanonicalPublication publication,
      String pmid,
      VenueId venueId,
      VenueInstanceAggregate venueInstance,
      Integer pubYear) {

    // 构建标识符
    PublicationIdentifiers identifiers = buildIdentifiers(pmid, extractDoi(publication));

    // 构建语言信息
    LanguageInfo languageInfo = buildLanguageInfo(publication);

    // 解析出版状态
    PublicationStatus publicationStatus =
        parsePublicationStatus(publication.getPublicationStatus());

    return PublicationAggregate.create(
        ProvenanceCode.PUBMED,
        identifiers,
        venueId,
        venueInstance.getId(),
        publication.getTitle(),
        publication.getOriginalTitle(),
        languageInfo,
        publicationStatus,
        null, // MediaType - 可从 publication.getMediaType() 获取，但需映射
        pubYear,
        publication.getAuthorsComplete(),
        null, // numberOfReferences - 可从 publication.getNumberOfReferences() 获取
        null // conflictOfInterest - 可从 publication.getConflictOfInterestStatement() 获取
        );
  }

  /// 构建标识符。
  private PublicationIdentifiers buildIdentifiers(String pmid, String doi) {
    // DOI 为空时只使用 PMID
    if (doi == null || doi.isBlank()) {
      return PublicationIdentifiers.ofPmid(pmid);
    }

    return PublicationIdentifiers.of(pmid, doi);
  }

  /// 构建语言信息。
  ///
  /// CanonicalPublication 中的 language 已是 ISO 639-1 代码。
  private LanguageInfo buildLanguageInfo(CanonicalPublication publication) {
    String isoCode = publication.getLanguage();

    // 默认英语
    if (isoCode == null || isoCode.isBlank()) {
      return LanguageInfo.ofCode("en");
    }

    return LanguageInfo.ofCode(isoCode);
  }

  /// 解析出版状态。
  private PublicationStatus parsePublicationStatus(String status) {
    if (status == null || status.isBlank()) {
      return PublicationStatus.PUBMED; // 默认状态
    }

    try {
      return PublicationStatus.fromCode(status);
    } catch (IllegalArgumentException e) {
      log.warn("未知的出版状态：{}，使用默认值 PUBMED", status);
      return PublicationStatus.PUBMED;
    }
  }
}
