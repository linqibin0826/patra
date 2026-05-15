package dev.linqibin.patra.catalog.infra.adapter.read;

import com.patra.starter.jpa.entity.BaseJpaEntity;
import dev.linqibin.commons.query.PageResult;
import dev.linqibin.commons.query.PagingParams;
import dev.linqibin.patra.catalog.domain.model.read.publication.PublicationDetailReadModel;
import dev.linqibin.patra.catalog.domain.model.read.publication.PublicationDetailReadModel.AbstractInfo;
import dev.linqibin.patra.catalog.domain.model.read.publication.PublicationDetailReadModel.IdentifierInfo;
import dev.linqibin.patra.catalog.domain.model.read.publication.PublicationDetailReadModel.KeywordInfo;
import dev.linqibin.patra.catalog.domain.model.read.publication.PublicationDetailReadModel.MeshHeadingInfo;
import dev.linqibin.patra.catalog.domain.model.read.publication.PublicationDetailReadModel.MeshHeadingInfo.MeshQualifierInfo;
import dev.linqibin.patra.catalog.domain.model.read.publication.PublicationFilter;
import dev.linqibin.patra.catalog.domain.model.read.publication.PublicationSummaryReadModel;
import dev.linqibin.patra.catalog.domain.port.read.PublicationReadPort;
import dev.linqibin.patra.catalog.infra.persistence.dao.KeywordDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.PublicationAbstractDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.PublicationDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.PublicationIdentifierDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.PublicationKeywordDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.PublicationMeshHeadingDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.PublicationMeshQualifierDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.VenueDao;
import dev.linqibin.patra.catalog.infra.persistence.entity.KeywordEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.PublicationEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.PublicationKeywordEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.PublicationMeshHeadingEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.PublicationMeshQualifierEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.VenueEntity;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/// 文献出版物 CQRS 读适配器。
///
/// 实现 [PublicationReadPort]，组合多个 DAO 查询构建读模型。
///
/// **列表查询策略**：主表分页 + 批量补充 venue 名称（每页 IN 查询开销极小）。
/// **详情查询策略**：多次独立 DAO 查询 + 手动组装（无 N+1 问题，查询可控）。
///
/// @author linqibin
/// @since 0.1.0
@Repository
@RequiredArgsConstructor
public class PublicationReadAdapter implements PublicationReadPort {

  private final PublicationDao publicationDao;
  private final VenueDao venueDao;
  private final PublicationAbstractDao publicationAbstractDao;
  private final PublicationIdentifierDao publicationIdentifierDao;
  private final PublicationKeywordDao publicationKeywordDao;
  private final KeywordDao keywordDao;
  private final PublicationMeshHeadingDao publicationMeshHeadingDao;
  private final PublicationMeshQualifierDao publicationMeshQualifierDao;
  private final PublicationReadModelMapper publicationReadModelMapper;

  /// 解析排序参数。
  ///
  /// 当 `sortBy` 为 "citedByCount" 时按被引次数降序排列，否则使用默认排序。
  ///
  /// @param sortBy 排序字段名（可为 null）
  /// @return 排序规则
  private Sort resolveSort(String sortBy) {
    if ("citedByCount".equals(sortBy)) {
      return Sort.by(Sort.Order.desc("citationCount"), Sort.Order.desc("id"));
    }
    return BaseJpaEntity.DEFAULT_SORT;
  }

  @Override
  public PageResult<PublicationSummaryReadModel> findPublicationPage(
      PagingParams paging, PublicationFilter filter) {
    Pageable pageable =
        PageRequest.of(paging.page() - 1, paging.pageSize(), resolveSort(filter.sortBy()));

    Page<PublicationEntity> entityPage =
        publicationDao.findPublicationPage(
            filter.keyword(),
            filter.yearFrom(),
            filter.yearTo(),
            filter.languageBase(),
            filter.isOa(),
            filter.oaStatus(),
            filter.venueId(),
            filter.venueInstanceId(),
            filter.pmid(),
            filter.doi(),
            filter.provenanceCode(),
            filter.publicationStatus(),
            pageable);

    // 批量获取 venue 名称
    Set<Long> venueIds =
        entityPage.getContent().stream()
            .map(PublicationEntity::getVenueId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    Map<Long, String> venueNameMap =
        venueIds.isEmpty()
            ? Map.of()
            : venueDao.findByIdIn(venueIds).stream()
                .collect(Collectors.toMap(VenueEntity::getId, VenueEntity::getTitle));

    List<PublicationSummaryReadModel> items =
        entityPage.getContent().stream()
            .map(entity -> toSummaryReadModel(entity, venueNameMap))
            .toList();

    return PageResult.of(items, paging.page(), paging.pageSize(), entityPage.getTotalElements());
  }

  @Override
  public Optional<PublicationDetailReadModel> findPublicationDetail(Long id) {
    return publicationDao.findById(id).map(this::toDetailReadModel);
  }

  @Override
  public List<PublicationSummaryReadModel> findTopByVenue(Long venueId, Integer since, int limit) {
    Objects.requireNonNull(venueId, "venueId must not be null");
    if (limit <= 0) return List.of();

    List<PublicationEntity> entities = publicationDao.findTopByVenue(venueId, since, limit);
    if (entities.isEmpty()) return List.of();

    // 单 venue 查询：venueName 一次取出，复用现有私有映射方法
    Map<Long, String> venueNameMap =
        venueDao.findById(venueId).map(v -> Map.of(venueId, v.getTitle())).orElseGet(Map::of);

    return entities.stream().map(entity -> toSummaryReadModel(entity, venueNameMap)).toList();
  }

  /// 将 PublicationEntity + venueNameMap 组装为 SummaryReadModel。
  private PublicationSummaryReadModel toSummaryReadModel(
      PublicationEntity entity, Map<Long, String> venueNameMap) {
    return new PublicationSummaryReadModel(
        entity.getId(),
        entity.getTitle(),
        entity.getPmid(),
        entity.getDoi(),
        entity.getPublicationYear(),
        entity.getLanguageCode(),
        entity.getIsOa(),
        entity.getOaStatus(),
        entity.getVenueId(),
        entity.getVenueId() != null ? venueNameMap.get(entity.getVenueId()) : null,
        entity.getCitationCount(),
        entity.getLastSyncedAt());
  }

  /// 将 PublicationEntity + 子表查询结果组装为 DetailReadModel。
  private PublicationDetailReadModel toDetailReadModel(PublicationEntity entity) {
    Long pubId = entity.getId();

    // Venue 名称
    String venueName =
        entity.getVenueId() != null
            ? venueDao.findById(entity.getVenueId()).map(VenueEntity::getTitle).orElse(null)
            : null;

    // 摘要
    List<AbstractInfo> abstracts =
        publicationAbstractDao.findByPublicationId(pubId).stream()
            .map(publicationReadModelMapper::toAbstractInfo)
            .toList();

    // 标识符
    List<IdentifierInfo> identifiers =
        publicationIdentifierDao.findByPublicationId(pubId).stream()
            .map(publicationReadModelMapper::toIdentifierInfo)
            .toList();

    // 关键词
    List<KeywordInfo> keywords = buildKeywordInfos(pubId);

    // MeSH 标引
    List<MeshHeadingInfo> meshHeadings = buildMeshHeadingInfos(pubId);

    return PublicationDetailReadModel.builder()
        .id(pubId)
        .provenanceCode(entity.getProvenanceCode())
        .title(entity.getTitle())
        .originalTitle(entity.getOriginalTitle())
        .pmid(entity.getPmid())
        .doi(entity.getDoi())
        .publicationYear(entity.getPublicationYear())
        .languageCode(entity.getLanguageCode())
        .languageRaw(entity.getLanguageRaw())
        .languageBase(entity.getLanguageBase())
        .publicationStatus(entity.getPublicationStatus())
        .mediaType(entity.getMediaType())
        .isOa(entity.getIsOa())
        .oaStatus(entity.getOaStatus())
        .venueId(entity.getVenueId())
        .venueName(venueName)
        .citationCount(entity.getCitationCount())
        .numberOfReferences(entity.getNumberOfReferences())
        .authorsComplete(entity.getAuthorsComplete())
        .conflictOfInterest(entity.getConflictOfInterest())
        .lastSyncedAt(entity.getLastSyncedAt())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .abstracts(abstracts)
        .identifiers(identifiers)
        .keywords(keywords)
        .meshHeadings(meshHeadings)
        .build();
  }

  /// 构建关键词信息列表（联合 PublicationKeyword + Keyword 表）。
  private List<KeywordInfo> buildKeywordInfos(Long publicationId) {
    List<PublicationKeywordEntity> pubKeywords =
        publicationKeywordDao.findByPublicationId(publicationId);
    if (pubKeywords.isEmpty()) {
      return List.of();
    }

    List<Long> keywordIds =
        pubKeywords.stream().map(PublicationKeywordEntity::getKeywordId).toList();
    Map<Long, KeywordEntity> keywordMap =
        keywordDao.findAllById(keywordIds).stream()
            .collect(Collectors.toMap(KeywordEntity::getId, kw -> kw));

    return pubKeywords.stream()
        .map(
            pk -> {
              KeywordEntity kw = keywordMap.get(pk.getKeywordId());
              return new KeywordInfo(
                  kw != null ? kw.getTerm() : null, pk.getMajor(), pk.getKeywordSet());
            })
        .toList();
  }

  /// 构建 MeSH 标引信息列表（联合 MeshHeading + MeshQualifier 表）。
  private List<MeshHeadingInfo> buildMeshHeadingInfos(Long publicationId) {
    List<PublicationMeshHeadingEntity> headings =
        publicationMeshHeadingDao.findByPublicationId(publicationId);
    if (headings.isEmpty()) {
      return List.of();
    }

    List<Long> headingIds = headings.stream().map(PublicationMeshHeadingEntity::getId).toList();
    Map<Long, List<PublicationMeshQualifierEntity>> qualifierMap =
        publicationMeshQualifierDao.findByPublicationMeshHeadingIdIn(headingIds).stream()
            .collect(
                Collectors.groupingBy(PublicationMeshQualifierEntity::getPublicationMeshHeadingId));

    return headings.stream()
        .map(
            heading -> {
              List<MeshQualifierInfo> qualifiers =
                  qualifierMap.getOrDefault(heading.getId(), List.of()).stream()
                      .map(publicationReadModelMapper::toMeshQualifierInfo)
                      .toList();
              return new MeshHeadingInfo(
                  heading.getDescriptorUi(), heading.getMajorTopic(), qualifiers);
            })
        .toList();
  }
}
