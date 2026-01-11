package com.patra.catalog.infra.adapter.persistence.converter.mapper;

import com.patra.catalog.domain.model.aggregate.AuthorAggregate;
import com.patra.catalog.domain.model.enums.AuthorStatus;
import com.patra.catalog.domain.model.enums.DataSourceCode;
import com.patra.catalog.domain.model.vo.author.AuthorId;
import com.patra.catalog.domain.model.vo.author.AuthorNameVariant;
import com.patra.catalog.domain.model.vo.author.Orcid;
import com.patra.catalog.infra.adapter.persistence.entity.AuthorEntity;
import com.patra.catalog.infra.adapter.persistence.entity.AuthorNameVariantEntity;
import com.patra.catalog.infra.adapter.persistence.entity.AuthorOrcidEntity;
import com.patra.starter.jpa.id.SnowflakeIdGenerator;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/// 作者 JPA 实体转换器。
///
/// **职责**：
///
/// - `AuthorAggregate` ↔ `AuthorEntity` 双向转换
/// - `AuthorNameVariant` ↔ `AuthorNameVariantEntity` 双向转换
/// - `Orcid` ↔ `AuthorOrcidEntity` 双向转换
///
/// **设计说明**：
///
/// - 聚合根转 Entity 时，子实体通过独立方法转换
/// - Entity 转聚合根时，使用 `restore()` 重建，再通过 `withXxx()` 添加子实体
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring")
public interface AuthorJpaMapper {

  // ========== 聚合根转换 ==========

  /// 将聚合根转换为 JPA 实体。
  ///
  /// **注意**：子实体（nameVariants、orcids）需要单独转换并设置。
  ///
  /// @param aggregate 作者聚合根
  /// @return JPA 实体
  @Mapping(target = "id", source = "id", qualifiedByName = "authorIdToLong")
  @Mapping(target = "status", source = "status", qualifiedByName = "statusToString")
  @Mapping(
      target = "provenanceCode",
      source = "provenanceCode",
      qualifiedByName = "provenanceToString")
  @Mapping(target = "nameVariants", ignore = true) // 子实体单独处理
  @Mapping(target = "orcids", ignore = true) // 子实体单独处理
  @Mapping(target = "extData", ignore = true)
  @Mapping(target = "version", ignore = true) // 由 JPA @Version 自动管理
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
  /// 使用 `AuthorAggregate.restore()` 工厂方法重建聚合根，
  /// 然后通过 `withNameVariants()` 和 `withOrcids()` 添加子实体。
  ///
  /// @param entity JPA 实体
  /// @return 作者聚合根
  default AuthorAggregate toAggregate(AuthorEntity entity) {
    if (entity == null) {
      return null;
    }

    // 重建聚合根
    AuthorAggregate aggregate =
        AuthorAggregate.restore(
            entity.getId() != null ? AuthorId.of(entity.getId()) : null,
            entity.getNormalizedKey(),
            entity.getDisplayName(),
            DataSourceCode.fromCode(entity.getProvenanceCode()),
            AuthorStatus.fromCode(entity.getStatus()),
            entity.getLastSyncedAt(),
            entity.getVersion());

    // 添加名字变体
    if (entity.getNameVariants() != null && !entity.getNameVariants().isEmpty()) {
      List<AuthorNameVariant> variants =
          entity.getNameVariants().stream().map(this::toNameVariant).toList();
      aggregate.withNameVariants(variants);
    }

    // 添加 ORCID
    if (entity.getOrcids() != null && !entity.getOrcids().isEmpty()) {
      List<Orcid> orcids = entity.getOrcids().stream().map(this::toOrcid).toList();
      aggregate.withOrcids(orcids);
    }

    // 清除变更追踪（重建的聚合根不应标记为已修改）
    aggregate.pullChildChanges();

    return aggregate;
  }

  // ========== 名字变体转换 ==========

  /// 将值对象转换为名字变体实体。
  ///
  /// @param variant 名字变体值对象
  /// @param author 所属作者实体
  /// @return 名字变体实体
  default AuthorNameVariantEntity toNameVariantEntity(
      AuthorNameVariant variant, AuthorEntity author) {
    if (variant == null) {
      return null;
    }
    return AuthorNameVariantEntity.builder()
        .id(SnowflakeIdGenerator.getId())
        .author(author)
        .lastName(variant.lastName())
        .foreName(variant.foreName())
        .initials(variant.initials())
        .fullString(variant.fullString())
        .build();
  }

  /// 将名字变体实体转换为值对象。
  ///
  /// @param entity 名字变体实体
  /// @return 名字变体值对象
  default AuthorNameVariant toNameVariant(AuthorNameVariantEntity entity) {
    if (entity == null) {
      return null;
    }
    return AuthorNameVariant.of(
        entity.getLastName(), entity.getForeName(), entity.getInitials(), entity.getFullString());
  }

  // ========== ORCID 转换 ==========

  /// 将值对象转换为 ORCID 实体。
  ///
  /// @param orcid ORCID 值对象
  /// @param author 所属作者实体
  /// @param isPrimary 是否为主要 ORCID
  /// @return ORCID 实体
  default AuthorOrcidEntity toOrcidEntity(Orcid orcid, AuthorEntity author, boolean isPrimary) {
    if (orcid == null) {
      return null;
    }
    return AuthorOrcidEntity.builder()
        .id(SnowflakeIdGenerator.getId())
        .author(author)
        .orcid(orcid.value())
        .primary(isPrimary)
        .build();
  }

  /// 将 ORCID 实体转换为值对象。
  ///
  /// @param entity ORCID 实体
  /// @return ORCID 值对象
  default Orcid toOrcid(AuthorOrcidEntity entity) {
    if (entity == null) {
      return null;
    }
    return Orcid.of(entity.getOrcid());
  }

  // ========== 类型转换 ==========

  /// 将 AuthorId 转换为 Long。
  @Named("authorIdToLong")
  default Long authorIdToLong(AuthorId id) {
    return id != null ? id.value() : null;
  }

  /// 将 AuthorStatus 转换为 String。
  @Named("statusToString")
  default String statusToString(AuthorStatus status) {
    return status != null ? status.getCode() : null;
  }

  /// 将 DataSourceCode 转换为 String。
  @Named("provenanceToString")
  default String provenanceToString(DataSourceCode code) {
    return code != null ? code.getCode() : null;
  }
}
