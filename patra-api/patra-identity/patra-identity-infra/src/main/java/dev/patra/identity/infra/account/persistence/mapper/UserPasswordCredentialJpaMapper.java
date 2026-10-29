package dev.patra.identity.infra.account.persistence.mapper;

import dev.patra.identity.domain.account.model.aggregate.UserPasswordCredential;
import dev.patra.identity.domain.account.model.vo.PasswordHash;
import dev.patra.identity.infra.account.persistence.entity.UserPasswordCredentialEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/// UserPasswordCredential 与 UserPasswordCredentialEntity 的转换。
@Mapper(componentModel = "spring")
public interface UserPasswordCredentialJpaMapper {

  /// 聚合转实体。
  ///
  /// @param credential 凭据
  /// @return 实体
  @Mapping(target = "passwordHash", expression = "java(credential.getPasswordHash().value())")
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdByName", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedByName", ignore = true)
  @Mapping(target = "recordRemarks", ignore = true)
  @Mapping(target = "ipAddress", ignore = true)
  UserPasswordCredentialEntity toEntity(UserPasswordCredential credential);

  /// 实体转聚合。
  ///
  /// @param entity 实体
  /// @return 凭据；实体为 `null` 时返回 `null`
  default UserPasswordCredential toAggregate(UserPasswordCredentialEntity entity) {
    if (entity == null) {
      return null;
    }
    return UserPasswordCredential.restore(
        entity.getId(),
        entity.getUserId(),
        PasswordHash.of(entity.getPasswordHash()),
        entity.getVersion());
  }
}
