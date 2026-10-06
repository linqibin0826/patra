package dev.linqibin.patra.identity.infra.adapter.persistence.converter.mapper;

import dev.linqibin.patra.identity.domain.model.aggregate.User;
import dev.linqibin.patra.identity.domain.model.enums.UserStatus;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import dev.linqibin.patra.identity.infra.adapter.persistence.entity.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/// User 与 UserEntity 的转换。
///
/// `id`、`version`、`createdAt`、`updatedAt` 同名映射：更新时带上版本才能做乐观锁检查。
/// 操作人字段由 JPA 审计管理。
@Mapper(componentModel = "spring")
public interface UserJpaMapper {

  /// 聚合转实体。
  ///
  /// @param user 用户
  /// @return 实体
  @Mapping(target = "email", expression = "java(user.getEmail().value())")
  @Mapping(target = "status", expression = "java(user.getStatus().name())")
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdByName", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedByName", ignore = true)
  @Mapping(target = "recordRemarks", ignore = true)
  @Mapping(target = "ipAddress", ignore = true)
  UserEntity toEntity(User user);

  /// 实体转聚合。
  ///
  /// @param entity 实体
  /// @return 用户；实体为 `null` 时返回 `null`
  default User toAggregate(UserEntity entity) {
    if (entity == null) {
      return null;
    }
    return User.restore(
        entity.getId(),
        new EmailAddress(entity.getEmail()),
        UserStatus.valueOf(entity.getStatus()),
        entity.getBannedAt(),
        entity.getVersion(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
