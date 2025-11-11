package com.patra.ingest.infra.integration.storage.acl;

import com.patra.catalog.api.dto.AuthorDTO;
import com.patra.catalog.api.dto.JournalDTO;
import com.patra.catalog.api.dto.LiteratureDTO;
import com.patra.common.model.CanonicalLiterature;
import com.patra.common.model.CanonicalLiterature.AuthorInfo;
import com.patra.common.model.CanonicalLiterature.JournalInfo;
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
  @Mapping(target = "authors", source = "authors", qualifiedByName = "mapAuthors")
  @Mapping(target = "journal", source = "journal", qualifiedByName = "mapJournal")
  @Mapping(target = "language", ignore = true)
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
  default List<AuthorDTO> mapAuthors(List<AuthorInfo> authors) {
    if (CollectionUtils.isEmpty(authors)) {
      return List.of();
    }
    return authors.stream()
        .map(
            author ->
                AuthorDTO.builder()
                    .lastName(author.getLastName())
                    .foreName(author.getForeName())
                    .initials(null)
                    .affiliations(resolveAffiliations(author.getAffiliation()))
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
  default JournalDTO mapJournal(JournalInfo journal) {
    if (journal == null) {
      return null;
    }
    return JournalDTO.builder()
        .title(journal.getTitle())
        .issn(journal.getIssn())
        .issnType(null)
        .publisher(journal.getPublisher())
        .country(null)
        .build();
  }

  /**
   * 将单个关联字符串解析为列表格式。
   *
   * @param affiliation 关联文本
   * @return 关联列表 (如果无文本则为空)
   */
  default List<String> resolveAffiliations(String affiliation) {
    if (!StringUtils.hasText(affiliation)) {
      return List.of();
    }
    return List.of(affiliation);
  }
}
