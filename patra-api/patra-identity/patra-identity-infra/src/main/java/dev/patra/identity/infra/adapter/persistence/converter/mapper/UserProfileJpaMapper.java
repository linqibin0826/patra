package dev.patra.identity.infra.adapter.persistence.converter.mapper;

import dev.patra.identity.domain.model.aggregate.UserProfile;
import dev.patra.identity.domain.model.vo.DisplayName;
import dev.patra.identity.infra.adapter.persistence.entity.UserProfileEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/// UserProfile 与 UserProfileEntity 的转换。
///
/// `id`、`version`、`createdAt`、`updatedAt` 同名映射：更新时带上版本才能做乐观锁检查。
/// 操作人字段由 JPA 审计管理。
@Mapper(componentModel = "spring", imports = DisplayName.class)
public interface UserProfileJpaMapper {

  /// 聚合转实体。
  ///
  /// @param profile 资料
  /// @return 实体
  @Mapping(
      target = "displayName",
      expression = "java(profile.getDisplayName().map(DisplayName::value).orElse(null))")
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdByName", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedByName", ignore = true)
  @Mapping(target = "recordRemarks", ignore = true)
  @Mapping(target = "ipAddress", ignore = true)
  UserProfileEntity toEntity(UserProfile profile);

  /// 实体转聚合。
  ///
  /// @param entity 实体
  /// @return 资料；实体为 `null` 时返回 `null`
  default UserProfile toAggregate(UserProfileEntity entity) {
    if (entity == null) {
      return null;
    }
    return UserProfile.restore()
        .id(entity.getId())
        .userId(entity.getUserId())
        .displayName(
            entity.getDisplayName() == null ? null : new DisplayName(entity.getDisplayName()))
        .version(entity.getVersion())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
