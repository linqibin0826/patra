package dev.patra.identity.infra.account.persistence.mapper;

import dev.patra.identity.domain.account.model.aggregate.FormerUsername;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.infra.account.persistence.entity.FormerUsernameEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/// FormerUsername 与 FormerUsernameEntity 的转换。行只插不改，审计时间由 JPA 审计管理。
@Mapper(componentModel = "spring")
public interface FormerUsernameJpaMapper {

  /// 聚合转实体。
  ///
  /// @param formerUsername 旧名记录
  /// @return 实体
  @Mapping(target = "username", expression = "java(formerUsername.getUsername().value())")
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdByName", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedByName", ignore = true)
  @Mapping(target = "recordRemarks", ignore = true)
  @Mapping(target = "ipAddress", ignore = true)
  FormerUsernameEntity toEntity(FormerUsername formerUsername);

  /// 实体转聚合。
  ///
  /// @param entity 实体
  /// @return 旧名记录；实体为 `null` 时返回 `null`
  default FormerUsername toAggregate(FormerUsernameEntity entity) {
    if (entity == null) {
      return null;
    }
    return FormerUsername.restore(
        entity.getId(),
        entity.getUserId(),
        new Username(entity.getUsername()),
        entity.getReplacedAt(),
        entity.getReservedUntil(),
        entity.getVersion());
  }
}
