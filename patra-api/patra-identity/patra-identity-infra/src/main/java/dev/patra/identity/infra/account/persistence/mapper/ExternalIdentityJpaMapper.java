package dev.patra.identity.infra.account.persistence.mapper;

import dev.patra.identity.domain.account.model.aggregate.ExternalIdentity;
import dev.patra.identity.domain.account.model.enums.IdentityProvider;
import dev.patra.identity.infra.account.persistence.entity.ExternalIdentityEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/// ExternalIdentity 与 ExternalIdentityEntity 的转换。
@Mapper(componentModel = "spring")
public interface ExternalIdentityJpaMapper {

  /// 聚合转实体。
  ///
  /// @param identity 外部身份
  /// @return 实体
  @Mapping(target = "provider", expression = "java(identity.getProvider().name())")
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdByName", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedByName", ignore = true)
  @Mapping(target = "recordRemarks", ignore = true)
  @Mapping(target = "ipAddress", ignore = true)
  ExternalIdentityEntity toEntity(ExternalIdentity identity);

  /// 实体转聚合。
  ///
  /// @param entity 实体
  /// @return 外部身份
  default ExternalIdentity toAggregate(ExternalIdentityEntity entity) {
    return ExternalIdentity.restore()
        .id(entity.getId())
        .userId(entity.getUserId())
        .provider(IdentityProvider.valueOf(entity.getProvider()))
        .subject(entity.getSubject())
        .accountLabel(entity.getAccountLabel())
        .linkedAt(entity.getLinkedAt())
        .lastLoginAt(entity.getLastLoginAt())
        .version(entity.getVersion())
        .build();
  }
}
