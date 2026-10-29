package dev.patra.identity.infra.account.persistence.mapper;

import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.infra.account.persistence.entity.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/// User 与 UserEntity 的转换。
///
/// `id`、`version`、`createdAt`、`updatedAt` 和各时间列同名映射：更新时带上版本才能做乐观锁检查。
/// 操作人字段由 JPA 审计管理。
@Mapper(componentModel = "spring", imports = EmailAddress.class)
public interface UserJpaMapper {

  /// 聚合转实体。
  ///
  /// @param user 用户
  /// @return 实体
  @Mapping(
      target = "email",
      expression = "java(user.getEmail().map(EmailAddress::value).orElse(null))")
  @Mapping(target = "username", expression = "java(user.getUsername().value())")
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
    return User.restore()
        .id(entity.getId())
        .email(entity.getEmail() == null ? null : new EmailAddress(entity.getEmail()))
        .emailVerifiedAt(entity.getEmailVerifiedAt())
        .username(new Username(entity.getUsername()))
        .usernameChangedAt(entity.getUsernameChangedAt())
        .status(UserStatus.valueOf(entity.getStatus()))
        .bannedAt(entity.getBannedAt())
        .deletionDueAt(entity.getDeletionDueAt())
        .deletionRemindedAt(entity.getDeletionRemindedAt())
        .deletedAt(entity.getDeletedAt())
        .version(entity.getVersion())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
