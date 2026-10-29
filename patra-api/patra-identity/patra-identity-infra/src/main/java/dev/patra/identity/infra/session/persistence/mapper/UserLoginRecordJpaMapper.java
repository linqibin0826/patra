package dev.patra.identity.infra.session.persistence.mapper;

import dev.patra.identity.domain.session.model.aggregate.UserLoginRecord;
import dev.patra.identity.domain.session.model.enums.DeviceClass;
import dev.patra.identity.domain.session.model.enums.LoginEndReason;
import dev.patra.identity.domain.session.model.vo.DeviceId;
import dev.patra.identity.infra.session.persistence.entity.UserLoginRecordEntity;
import dev.patra.tool.core.security.ClientType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/// UserLoginRecord 与 UserLoginRecordEntity 的转换。
///
/// `id`、`version`、`createdAt`、`updatedAt` 同名映射：更新时带上版本才能做乐观锁检查。
/// 操作人字段由 JPA 审计管理。
@Mapper(componentModel = "spring")
public interface UserLoginRecordJpaMapper {

  /// 聚合转实体。
  ///
  /// @param login 登录记录
  /// @return 实体
  @Mapping(target = "clientType", expression = "java(login.getClientType().name())")
  @Mapping(
      target = "deviceId",
      expression = "java(login.getDeviceId().map(device -> device.value()).orElse(null))")
  @Mapping(target = "deviceClass", expression = "java(login.getDeviceClass().name())")
  @Mapping(
      target = "endReason",
      expression = "java(login.getEndReason() == null ? null : login.getEndReason().name())")
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdByName", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedByName", ignore = true)
  @Mapping(target = "recordRemarks", ignore = true)
  @Mapping(target = "ipAddress", ignore = true)
  UserLoginRecordEntity toEntity(UserLoginRecord login);

  /// 实体转聚合。
  ///
  /// @param entity 实体
  /// @return 登录记录；实体为 `null` 时返回 `null`
  default UserLoginRecord toAggregate(UserLoginRecordEntity entity) {
    if (entity == null) {
      return null;
    }
    return UserLoginRecord.restore()
        .id(entity.getId())
        .userId(entity.getUserId())
        .clientType(ClientType.valueOf(entity.getClientType()))
        .deviceId(entity.getDeviceId() == null ? null : new DeviceId(entity.getDeviceId()))
        .userAgent(entity.getUserAgent())
        .deviceName(entity.getDeviceName())
        .deviceClass(DeviceClass.valueOf(entity.getDeviceClass()))
        .clientIp(entity.getClientIp())
        .ipRegion(entity.getIpRegion())
        .expiresAt(entity.getExpiresAt())
        .endedAt(entity.getEndedAt())
        .endReason(
            entity.getEndReason() == null ? null : LoginEndReason.valueOf(entity.getEndReason()))
        .version(entity.getVersion())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
