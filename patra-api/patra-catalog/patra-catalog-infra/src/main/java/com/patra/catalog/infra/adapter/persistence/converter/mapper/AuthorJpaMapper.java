package com.patra.catalog.infra.adapter.persistence.converter.mapper;

import com.patra.catalog.domain.model.aggregate.AuthorAggregate;
import com.patra.catalog.domain.model.vo.author.AuthorId;
import com.patra.catalog.domain.model.vo.author.AuthorName;
import com.patra.catalog.domain.model.vo.author.Orcid;
import com.patra.catalog.domain.model.vo.common.DedupKey;
import com.patra.catalog.infra.adapter.persistence.entity.AuthorEntity;
import com.patra.catalog.infra.adapter.persistence.entity.embeddable.AuthorNameEmbeddable;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/// 作者 JPA 实体转换器。
///
/// **职责**：
///
/// - `AuthorAggregate` ↔ `AuthorEntity` 双向转换
/// - 嵌入式值对象（`AuthorName` ↔ `AuthorNameEmbeddable`）的映射
/// - 简单值对象（`Orcid`、`DedupKey`、`AuthorId`）与基本类型的映射
///
/// **嵌入式值对象设计**：
///
/// - `AuthorName` record 映射为 `AuthorNameEmbeddable` 嵌入式对象
/// - 字段直接展开到 `cat_author` 表中（lastName, foreName, initials, suffix）
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring")
public interface AuthorJpaMapper {

  /// 将聚合根转换为 JPA 实体。
  ///
  /// @param aggregate 作者聚合根
  /// @return JPA 实体
  @Mapping(target = "id", source = "id", qualifiedByName = "authorIdToLong")
  @Mapping(target = "name", source = "name", qualifiedByName = "authorNameToEmbeddable")
  @Mapping(target = "orcid", source = "orcid", qualifiedByName = "orcidToString")
  @Mapping(target = "dedupKey", source = "dedupKey", qualifiedByName = "dedupKeyToString")
  @Mapping(target = "authorMetadata", ignore = true) // metadataJson 需要特殊处理
  @Mapping(target = "version", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdByName", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedByName", ignore = true)
  @Mapping(target = "ipAddress", ignore = true)
  @Mapping(target = "recordRemarks", ignore = true)
  AuthorEntity toEntity(AuthorAggregate aggregate);

  /// 将 JPA 实体转换为聚合根。
  ///
  /// 使用 `AuthorAggregate.restore()` 工厂方法重建聚合根。
  ///
  /// @param entity JPA 实体
  /// @return 作者聚合根
  default AuthorAggregate toAggregate(AuthorEntity entity) {
    if (entity == null) {
      return null;
    }

    return AuthorAggregate.restore(
        entity.getId() != null ? AuthorId.of(entity.getId()) : null,
        embeddableToAuthorName(entity.getName()),
        entity.getOrganizationName(),
        entity.getOrcid() != null ? Orcid.of(entity.getOrcid()) : null,
        entity.getResearcherId(),
        entity.getScopusId(),
        entity.getEmail(),
        entity.getDedupKey() != null ? DedupKey.fromHash(entity.getDedupKey()) : null,
        entity.getEqualContribution() != null && entity.getEqualContribution(),
        entity.getValid() != null && entity.getValid(),
        entity.getVersion());
  }

  /// 将 AuthorId 转换为 Long。
  @Named("authorIdToLong")
  default Long authorIdToLong(AuthorId id) {
    return id != null ? id.value() : null;
  }

  /// 将 AuthorName 转换为 AuthorNameEmbeddable。
  @Named("authorNameToEmbeddable")
  default AuthorNameEmbeddable authorNameToEmbeddable(AuthorName name) {
    if (name == null) {
      return null;
    }
    return new AuthorNameEmbeddable(
        name.lastName(), name.foreName(), name.initials(), name.suffix());
  }

  /// 将 AuthorNameEmbeddable 转换为 AuthorName。
  default AuthorName embeddableToAuthorName(AuthorNameEmbeddable embeddable) {
    if (embeddable == null) {
      return null;
    }
    // AuthorName 需要至少一个字段不为空
    if (embeddable.getLastName() == null
        && embeddable.getForeName() == null
        && embeddable.getInitials() == null) {
      return null;
    }
    return AuthorName.of(
        embeddable.getLastName(),
        embeddable.getForeName(),
        embeddable.getInitials(),
        embeddable.getSuffix());
  }

  /// 将 Orcid 转换为 String。
  @Named("orcidToString")
  default String orcidToString(Orcid orcid) {
    return orcid != null ? orcid.value() : null;
  }

  /// 将 DedupKey 转换为 String。
  @Named("dedupKeyToString")
  default String dedupKeyToString(DedupKey dedupKey) {
    return dedupKey != null ? dedupKey.value() : null;
  }
}
