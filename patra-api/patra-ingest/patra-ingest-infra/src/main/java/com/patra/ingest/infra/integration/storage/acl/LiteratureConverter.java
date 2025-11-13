package com.patra.ingest.infra.integration.storage.acl;

import com.patra.catalog.api.dto.AuthorDTO;
import com.patra.catalog.api.dto.JournalDTO;
import com.patra.catalog.api.dto.LiteratureDTO;
import com.patra.common.model.CanonicalLiterature;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 防腐层 (ACL) 转换器,用于将采集领域模型转换为目录 API DTO。
 *
 * <p>此 MapStruct 转换器通过提供显式的版本化映射,保护采集限界上下文免受外部 API 契约的影响。 对目录 API 结构的更改将隔离在此转换器中。
 *
 * <p>转换规则:
 *
 * <ul>
 *   <li>CanonicalLiterature → LiteratureDTO (目录外部 API 格式)
 *   <li>AuthorInfo → AuthorDTO (带关联归一化)
 *   <li>JournalInfo → JournalDTO (期刊元数据)
 * </ul>
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface LiteratureConverter {

  /**
   * 将领域 CanonicalLiterature 转换为目录 LiteratureDTO。
   *
   * @param source 领域文献模型
   * @return 目录 API DTO
   */
  @Mapping(target = "abstractText", source = "abstractContent", qualifiedByName = "mapAbstract")
  @Mapping(target = "authors", source = "authors", qualifiedByName = "mapAuthors")
  @Mapping(target = "journal", source = "journal", qualifiedByName = "mapJournal")
  @Mapping(target = "identifiers", source = "identifiers", qualifiedByName = "mapIdentifiers")
  @Mapping(target = "publicationDate", source = "dates", qualifiedByName = "mapPublicationDate")
  @Mapping(target = "keywords", source = "keywords", qualifiedByName = "mapKeywords")
  @Mapping(target = "language", source = "language")
  @Mapping(target = "publicationTypes", expression = "java(java.util.List.of())")
  LiteratureDTO toDto(CanonicalLiterature source);

  /**
   * 将 CanonicalLiterature 列表转换为 LiteratureDTO 列表。
   *
   * @param sources 领域文献列表
   * @return 目录 API DTO 列表
   */
  List<LiteratureDTO> toDto(List<CanonicalLiterature> sources);

  /**
   * 将领域作者映射为目录 AuthorDTO。
   *
   * @param authors 领域作者列表
   * @return 目录作者 DTO 列表
   */
  @Named("mapAuthors")
  default List<AuthorDTO> mapAuthors(List<CanonicalLiterature.Author> authors) {
    if (CollectionUtils.isEmpty(authors)) {
      return List.of();
    }
    return authors.stream()
        .map(
            author ->
                AuthorDTO.builder()
                    .lastName(author.getLastName())
                    .foreName(author.getForeName())
                    .initials(author.getInitials())
                    .affiliations(resolveAffiliations(author.getAffiliations()))
                    .identifier(null)
                    .identifierSource(null)
                    .build())
        .toList();
  }

  /**
   * 将领域期刊映射为目录 JournalDTO。
   *
   * @param journal 领域期刊
   * @return 目录期刊 DTO 或 null
   */
  @Named("mapJournal")
  default JournalDTO mapJournal(CanonicalLiterature.Journal journal) {
    if (journal == null) {
      return null;
    }
    return JournalDTO.builder()
        .title(journal.getTitle())
        .issn(journal.getIssn())
        .issnType(journal.getIssnType())
        .publisher(journal.getPublisher())
        .country(journal.getCountry())
        .build();
  }

  /**
   * 将机构列表转换为字符串列表。
   *
   * @param affiliations 机构列表
   * @return 机构名称列表 (如果为空则返回空列表)
   */
  default List<String> resolveAffiliations(List<CanonicalLiterature.Affiliation> affiliations) {
    if (CollectionUtils.isEmpty(affiliations)) {
      return List.of();
    }
    return affiliations.stream()
        .map(CanonicalLiterature.Affiliation::getName)
        .filter(StringUtils::hasText)
        .toList();
  }

  /**
   * 将 Abstract 对象转换为纯文本字符串。
   *
   * @param abstractContent 摘要对象
   * @return 摘要纯文本 (如果为 null 则返回 null)
   */
  @Named("mapAbstract")
  default String mapAbstract(CanonicalLiterature.Abstract abstractContent) {
    if (abstractContent == null) {
      return null;
    }
    return abstractContent.getText();
  }

  /**
   * 将标识符列表转换为 Map。
   *
   * @param identifiers 标识符列表
   * @return 标识符 Map (如果为 null 或空则返回 null)
   */
  @Named("mapIdentifiers")
  default java.util.Map<String, String> mapIdentifiers(
      List<CanonicalLiterature.Identifier> identifiers) {
    if (CollectionUtils.isEmpty(identifiers)) {
      return null;
    }
    return identifiers.stream()
        .filter(id -> StringUtils.hasText(id.getType()) && StringUtils.hasText(id.getValue()))
        .collect(java.util.stream.Collectors.toMap(
            CanonicalLiterature.Identifier::getType,
            CanonicalLiterature.Identifier::getValue,
            (v1, v2) -> v1)); // 如果有重复的 key，保留第一个
  }

  /**
   * 从 PublicationDates 对象中提取主要出版日期。
   *
   * @param dates 出版日期对象
   * @return 主要出版日期 (如果为 null 则返回 null)
   */
  @Named("mapPublicationDate")
  default java.time.LocalDate mapPublicationDate(CanonicalLiterature.PublicationDates dates) {
    if (dates == null) {
      return null;
    }
    return dates.getPublished();
  }

  /**
   * 将关键词集合列表转换为扁平的关键词字符串列表。
   *
   * @param keywordSets 关键词集合列表
   * @return 关键词字符串列表 (如果为 null 或空则返回 null)
   */
  @Named("mapKeywords")
  default List<String> mapKeywords(List<CanonicalLiterature.KeywordSet> keywordSets) {
    if (CollectionUtils.isEmpty(keywordSets)) {
      return null;
    }
    return keywordSets.stream()
        .filter(keywordSet -> !CollectionUtils.isEmpty(keywordSet.getKeywords()))
        .flatMap(keywordSet -> keywordSet.getKeywords().stream())
        .map(CanonicalLiterature.Keyword::getTerm)
        .filter(StringUtils::hasText)
        .toList();
  }
}
