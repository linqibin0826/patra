package com.patra.catalog.infra.adapter.batch.publication;

import com.patra.catalog.domain.model.aggregate.PublicationAggregate;
import com.patra.catalog.domain.model.aggregate.VenueInstanceAggregate;
import com.patra.catalog.domain.model.enums.PublicationStatus;
import com.patra.catalog.domain.model.vo.publication.LanguageInfo;
import com.patra.catalog.domain.model.vo.publication.PublicationIdentifiers;
import com.patra.catalog.domain.model.vo.publication.pubmed.PubmedArticle;
import com.patra.catalog.domain.model.vo.venue.VenueId;
import com.patra.catalog.domain.model.vo.venueinstance.JournalInstanceParams;
import com.patra.catalog.domain.port.gateway.VenueInstanceGateway;
import com.patra.catalog.domain.port.repository.PublicationRepository;
import com.patra.catalog.infra.adapter.batch.publication.cache.VenueCache;
import com.patra.common.enums.ProvenanceCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
/// 4. 聚合根构建：将 PubmedArticle DTO 转换为 PublicationAggregate
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
    implements ItemProcessor<PubmedArticle, PublicationAggregate> {

  private final PublicationRepository publicationRepository;
  private final VenueCache venueCache;
  private final VenueInstanceGateway venueInstanceGateway;

  /// 语言代码映射（PubMed 3 字母代码 → BCP 47）。
  private static final Map<String, String> LANGUAGE_CODE_MAP =
      Map.ofEntries(
          Map.entry("eng", "en"),
          Map.entry("chi", "zh"),
          Map.entry("jpn", "ja"),
          Map.entry("kor", "ko"),
          Map.entry("ger", "de"),
          Map.entry("fre", "fr"),
          Map.entry("spa", "es"),
          Map.entry("ita", "it"),
          Map.entry("por", "pt"),
          Map.entry("rus", "ru"),
          Map.entry("ara", "ar"),
          Map.entry("dut", "nl"),
          Map.entry("pol", "pl"),
          Map.entry("tur", "tr"),
          Map.entry("hun", "hu"),
          Map.entry("cze", "cs"),
          Map.entry("dan", "da"),
          Map.entry("fin", "fi"),
          Map.entry("gre", "el"),
          Map.entry("heb", "he"),
          Map.entry("nor", "no"),
          Map.entry("swe", "sv"),
          Map.entry("ukr", "uk"));

  @Override
  public PublicationAggregate process(PubmedArticle article) throws Exception {
    String pmid = article.pmid();

    // 1. 去重检查
    if (publicationRepository.existsByPmid(pmid)) {
      log.debug("跳过已存在的文献：PMID={}", pmid);
      return null;
    }

    // 2. Venue 匹配
    List<String> issns = collectIssns(article);
    Optional<VenueId> venueIdOpt = venueCache.findByPriority(article.nlmUniqueId(), issns);

    if (venueIdOpt.isEmpty()) {
      log.debug("无法匹配 Venue，跳过文献：PMID={}, NLM={}, ISSNs={}", pmid, article.nlmUniqueId(), issns);
      return null;
    }

    VenueId venueId = venueIdOpt.get();

    // 3. VenueInstance 创建/获取
    JournalInstanceParams params =
        JournalInstanceParams.builder()
            .venueId(venueId)
            .volume(article.volume())
            .issue(article.issue())
            .publicationYear(article.pubYear())
            .publicationMonth(article.pubMonth())
            .publicationDay(article.pubDay())
            .build();
    VenueInstanceAggregate venueInstance = venueInstanceGateway.findOrCreateJournalInstance(params);

    // 4. 构建 PublicationAggregate
    return buildPublicationAggregate(article, venueId, venueInstance);
  }

  /// 收集所有 ISSN（按优先级排序：Linking → Print → Electronic）。
  private List<String> collectIssns(PubmedArticle article) {
    List<String> issns = new ArrayList<>(3);
    if (article.issnLinking() != null && !article.issnLinking().isBlank()) {
      issns.add(article.issnLinking());
    }
    if (article.issnPrint() != null && !article.issnPrint().isBlank()) {
      issns.add(article.issnPrint());
    }
    if (article.issnElectronic() != null && !article.issnElectronic().isBlank()) {
      issns.add(article.issnElectronic());
    }
    return issns;
  }

  /// 构建 PublicationAggregate。
  private PublicationAggregate buildPublicationAggregate(
      PubmedArticle article, VenueId venueId, VenueInstanceAggregate venueInstance) {

    // 构建标识符
    PublicationIdentifiers identifiers = buildIdentifiers(article);

    // 构建语言信息
    LanguageInfo languageInfo = buildLanguageInfo(article);

    // 解析出版状态
    PublicationStatus publicationStatus = parsePublicationStatus(article.publicationStatus());

    return PublicationAggregate.create(
        ProvenanceCode.PUBMED,
        identifiers,
        venueId,
        venueInstance.getId(),
        article.articleTitle(),
        article.vernacularTitle(),
        languageInfo,
        publicationStatus,
        null, // MediaType - PubMed 不直接提供此信息
        article.pubYear(),
        article.authorsComplete(),
        null, // numberOfReferences - PubMed 不提供
        null // conflictOfInterest - 需要从其他字段提取
        );
  }

  /// 构建标识符。
  private PublicationIdentifiers buildIdentifiers(PubmedArticle article) {
    String pmid = article.pmid();
    String doi = article.doi();

    // DOI 为空时只使用 PMID
    if (doi == null || doi.isBlank()) {
      return PublicationIdentifiers.ofPmid(pmid);
    }

    return PublicationIdentifiers.of(pmid, doi);
  }

  /// 构建语言信息。
  private LanguageInfo buildLanguageInfo(PubmedArticle article) {
    List<String> languages = article.languages();

    // 默认英语
    if (languages == null || languages.isEmpty()) {
      return LanguageInfo.ofCode("en");
    }

    // 使用第一个语言
    String rawLang = languages.getFirst();
    String code = LANGUAGE_CODE_MAP.getOrDefault(rawLang.toLowerCase(), "en");

    return LanguageInfo.of(rawLang, code);
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
