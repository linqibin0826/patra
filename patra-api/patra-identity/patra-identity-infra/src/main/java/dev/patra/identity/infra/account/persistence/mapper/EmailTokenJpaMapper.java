package dev.patra.identity.infra.account.persistence.mapper;

import dev.patra.identity.domain.account.model.aggregate.EmailToken;
import dev.patra.identity.domain.account.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.infra.account.persistence.entity.EmailTokenEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/// EmailToken 与 EmailTokenEntity 的转换。`id`、`version`、时间列同名映射；操作人字段由 JPA 审计管理。
@Mapper(componentModel = "spring")
public interface EmailTokenJpaMapper {

  /// 聚合转实体。
  ///
  /// @param token 令牌
  /// @return 实体
  @Mapping(target = "purpose", expression = "java(token.getPurpose().name())")
  @Mapping(target = "email", expression = "java(token.getEmail().value())")
  @Mapping(target = "requestedIp", expression = "java(token.getRequestedIp().orElse(null))")
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdByName", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedByName", ignore = true)
  @Mapping(target = "recordRemarks", ignore = true)
  @Mapping(target = "ipAddress", ignore = true)
  EmailTokenEntity toEntity(EmailToken token);

  /// 实体转聚合。
  ///
  /// @param entity 实体
  /// @return 令牌；实体为 `null` 时返回 `null`
  default EmailToken toAggregate(EmailTokenEntity entity) {
    if (entity == null) {
      return null;
    }
    return EmailToken.restore()
        .id(entity.getId())
        .userId(entity.getUserId())
        .purpose(EmailTokenPurpose.valueOf(entity.getPurpose()))
        .email(new EmailAddress(entity.getEmail()))
        .tokenHash(entity.getTokenHash())
        .expiresAt(entity.getExpiresAt())
        .consumedAt(entity.getConsumedAt())
        .revokedAt(entity.getRevokedAt())
        .requestedIp(entity.getRequestedIp())
        .version(entity.getVersion())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
