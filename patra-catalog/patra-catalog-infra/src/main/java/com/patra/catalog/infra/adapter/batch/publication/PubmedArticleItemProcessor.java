package com.patra.catalog.infra.adapter.batch.publication;

import com.patra.catalog.domain.model.aggregate.PublicationAggregate;
import com.patra.catalog.domain.model.aggregate.VenueInstanceAggregate;
import com.patra.catalog.domain.model.enums.PublicationStatus;
import com.patra.catalog.domain.model.vo.publication.LanguageInfo;
import com.patra.catalog.domain.model.vo.venue.VenueId;
import com.patra.catalog.domain.model.vo.venueinstance.JournalInstanceParams;
import com.patra.catalog.domain.port.gateway.VenueInstanceGateway;
import com.patra.catalog.domain.port.lookup.LanguageLookupPort;
import com.patra.catalog.domain.port.lookup.VenueLookupPort;
import com.patra.catalog.domain.port.repository.PublicationRepository;
import com.patra.common.enums.ProvenanceCode;
import com.patra.common.model.CanonicalPublication;
import com.patra.common.model.CanonicalPublication.Identifier;
import com.patra.common.model.CanonicalPublication.Journal;
import com.patra.common.model.CanonicalPublication.PublicationDates;
import com.patra.common.model.enums.PublicationIdentifierType;
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
  private final VenueLookupPort venueLookupPort;
  private final VenueInstanceGateway venueInstanceGateway;
  private final LanguageLookupPort languageLookupPort;

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
    // TODO: 当前仅支持期刊文章，后续需扩展支持其他出版物类型
    //   - Book/Book Chapter：PubMed 通过 NCBI Bookshelf 索引的书籍章节，使用 <Book> 元素
    //   - Preprint：bioRxiv/medRxiv 预印本
    //   - Dataset：数据集引用
    Journal journal = publication.getJournal();
    if (journal == null) {
      log.debug("跳过非期刊类型文献：PMID={}", pmid);
      return null;
    }

    String nlmUniqueId = journal.getNlmUniqueId();
    List<String> issns = collectIssns(journal);
    Optional<VenueId> venueIdOpt = venueLookupPort.findByPriority(nlmUniqueId, issns);

    // TODO: 没有匹配到 Venue 时不应该跳过文献，应该：
    //   1. 创建 PublicationAggregate（venueId 和 venueInstanceId 为 null）
    //   2. 后续通过异步任务或事件驱动方式补充 Venue 关联
    //   3. 或者将这些"孤儿文献"记录到专门的队列/表中待处理
    if (venueIdOpt.isEmpty()) {
      log.warn("无法匹配 Venue，跳过文献：PMID={}, NLM={}, ISSNs={}", pmid, nlmUniqueId, issns);
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
        .filter(id -> PublicationIdentifierType.PMID == id.getType())
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
        .filter(id -> PublicationIdentifierType.DOI == id.getType())
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

    // 构建语言信息
    LanguageInfo languageInfo = buildLanguageInfo(publication);

    // 解析出版状态
    PublicationStatus publicationStatus =
        parsePublicationStatus(publication.getPublicationStatus());

    // 提取 DOI
    String doi = extractDoi(publication);

    return PublicationAggregate.create(
        ProvenanceCode.PUBMED,
        pmid,
        doi,
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

  /// 构建语言信息。
  ///
  /// PubMed 使用 ISO 639-3 三字母代码（如 "eng", "chi"），
  /// 通过 LanguageLookupPort 转换为项目标准 BCP 47 代码（如 "en", "zh"）。
  private LanguageInfo buildLanguageInfo(CanonicalPublication publication) {
    String iso639Code = publication.getLanguage();

    // 默认英语
    if (iso639Code == null || iso639Code.isBlank()) {
      return LanguageInfo.ofCode("en");
    }

    // 通过 LanguageLookupPort 解析语言代码
    String bcp47Code = languageLookupPort.resolve(iso639Code);

    // 如果解析失败（返回 null 或 "unknown"），使用默认英语
    if (bcp47Code == null || LanguageLookupPort.UNKNOWN_LANGUAGE.equals(bcp47Code)) {
      log.warn("无法解析语言代码：{}，使用默认值 en", iso639Code);
      return LanguageInfo.ofCode("en");
    }

    return LanguageInfo.ofCode(bcp47Code);
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
